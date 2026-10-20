import { GoogleDriveSyncPayload, ReadingProgress, LibraryItem } from '../types/index.js';

export interface SyncResult {
  success: boolean;
  mergedPayload?: GoogleDriveSyncPayload;
  error?: string;
  syncedAt: number;
}

/**
 * Google Drive Personal Cloud Sync Engine
 * Uses the user's private Google Drive `appDataFolder`
 * Guarantees zero-server hosting cost, complete privacy, and cross-device sync between PC and Android.
 */
export class GoogleDriveSyncService {
  private accessToken?: string;
  private readonly appDataFileName = 'yomira_sync_v1.json';
  private readonly driveApiBase = 'https://www.googleapis.com/drive/v3';
  private readonly uploadApiBase = 'https://www.googleapis.com/upload/drive/v3';

  constructor(accessToken?: string) {
    this.accessToken = accessToken;
  }

  setAccessToken(token: string) {
    this.accessToken = token;
  }

  get isConnected(): boolean {
    return Boolean(this.accessToken);
  }

  /**
   * Pushes local state to Google Drive appDataFolder
   */
  async pushToDrive(payload: GoogleDriveSyncPayload): Promise<SyncResult> {
    if (!this.accessToken) {
      return { success: false, error: 'Google Drive accessToken is not set', syncedAt: Date.now() };
    }

    try {
      const fileId = await this.findSyncFileId();
      const content = JSON.stringify(payload, null, 2);

      if (fileId) {
        // Update existing file
        const updateUrl = `${this.uploadApiBase}/files/${fileId}?uploadType=media`;
        const res = await fetch(updateUrl, {
          method: 'PATCH',
          headers: {
            'Authorization': `Bearer ${this.accessToken}`,
            'Content-Type': 'application/json'
          },
          body: content
        });

        if (!res.ok) throw new Error(`Failed to update Drive sync file: ${res.statusText}`);
      } else {
        // Create new file inside appDataFolder
        const metadata = {
          name: this.appDataFileName,
          parents: ['appDataFolder']
        };

        const form = new FormData();
        form.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
        form.append('file', new Blob([content], { type: 'application/json' }));

        const createUrl = `${this.uploadApiBase}/files?uploadType=multipart`;
        const res = await fetch(createUrl, {
          method: 'POST',
          headers: { 'Authorization': `Bearer ${this.accessToken}` },
          body: form
        });

        if (!res.ok) throw new Error(`Failed to create Drive sync file: ${res.statusText}`);
      }

      return { success: true, mergedPayload: payload, syncedAt: Date.now() };
    } catch (err: any) {
      return { success: false, error: err.message || 'Unknown sync error', syncedAt: Date.now() };
    }
  }

  /**
   * Pulls the latest sync payload from Google Drive
   */
  async pullFromDrive(): Promise<GoogleDriveSyncPayload | null> {
    if (!this.accessToken) return null;

    try {
      const fileId = await this.findSyncFileId();
      if (!fileId) return null;

      const downloadUrl = `${this.driveApiBase}/files/${fileId}?alt=media`;
      const res = await fetch(downloadUrl, {
        headers: { 'Authorization': `Bearer ${this.accessToken}` }
      });

      if (!res.ok) return null;
      return (await res.json()) as GoogleDriveSyncPayload;
    } catch {
      return null;
    }
  }

  /**
   * Smart Multi-Device Conflict Resolver (Last-Write-Wins based on item timestamps)
   * Resolves reading progress, library status and installed repos across PC, Phone and Tablet.
   */
  mergeStates(
    local: GoogleDriveSyncPayload,
    remote: GoogleDriveSyncPayload
  ): GoogleDriveSyncPayload {
    // 1. Merge Library (keep latest added or updated)
    const libraryMap = new Map<string, LibraryItem>();
    for (const item of [...local.library, ...remote.library]) {
      const existing = libraryMap.get(item.seriesId);
      if (!existing || item.addedAt > existing.addedAt) {
        libraryMap.set(item.seriesId, item);
      }
    }

    // 2. Merge Reading Progress (highest chapter / page with newest timestamp wins)
    const progressMap = new Map<string, ReadingProgress>();
    for (const item of [...local.progress, ...remote.progress]) {
      const existing = progressMap.get(item.seriesId);
      if (!existing) {
        progressMap.set(item.seriesId, item);
      } else {
        // If one is ahead in chapters, or if same chapter, highest page with newer timestamp
        const isItemAhead = item.chapterNumber > existing.chapterNumber ||
          (item.chapterNumber === existing.chapterNumber && item.pageIndex > existing.pageIndex);
        
        if (isItemAhead || item.lastReadAt > existing.lastReadAt) {
          progressMap.set(item.seriesId, item);
        }
      }
    }

    // 3. Merge installed repositories
    const installedRepoUrls = Array.from(new Set([
      ...local.installedRepoUrls,
      ...remote.installedRepoUrls
    ]));

    // 4. Merge pinned extensions
    const pinnedExtensions = {
      ...remote.pinnedExtensions,
      ...local.pinnedExtensions
    };

    return {
      version: Math.max(local.version, remote.version),
      deviceId: local.deviceId,
      deviceName: local.deviceName,
      lastSyncedAt: Date.now(),
      library: Array.from(libraryMap.values()),
      progress: Array.from(progressMap.values()),
      installedRepoUrls,
      pinnedExtensions,
      userSettings: local.lastSyncedAt > remote.lastSyncedAt ? local.userSettings : remote.userSettings
    };
  }

  private async findSyncFileId(): Promise<string | null> {
    if (!this.accessToken) return null;
    const url = new URL(`${this.driveApiBase}/files`);
    url.searchParams.set('spaces', 'appDataFolder');
    url.searchParams.set('q', `name = '${this.appDataFileName}' and trashed = false`);
    url.searchParams.set('fields', 'files(id, name)');

    const res = await fetch(url.toString(), {
      headers: { 'Authorization': `Bearer ${this.accessToken}` }
    });

    if (!res.ok) return null;
    const json = await res.json() as any;
    const files = json.files || [];
    return files.length > 0 ? files[0].id : null;
  }
}
