import { RepositoryManager, ExtensionRuntime } from '../dist/index.js';

async function runRepositoryManagerTests() {
  console.log('=== [YOMIRA REPOSITORY MANAGER TEST] ===\n');

  const runtime = new ExtensionRuntime();
  const initialRepos = [
    'https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json'
  ];

  const repoManager = new RepositoryManager(runtime, initialRepos);

  // 1. Repo URL management
  console.log('-> 1. Testing Repo URL management:');
  const urls = repoManager.getRepoUrls();
  console.log(`   Initial repos count: ${urls.length}`);
  if (urls.length !== 1 || !urls[0].includes('keiyoushi')) {
    throw new Error('Initial repo URLs failed');
  }

  repoManager.addRepoUrl('https://raw.githubusercontent.com/yomira/custom-repo/index.json');
  if (repoManager.getRepoUrls().length !== 2) {
    throw new Error('Add repo URL failed');
  }

  repoManager.removeRepoUrl('https://raw.githubusercontent.com/yomira/custom-repo/index.json');
  if (repoManager.getRepoUrls().length !== 1) {
    throw new Error('Remove repo URL failed');
  }
  console.log('   [PASS] Add/Remove repo URLs works.');

  // 2. Fetch available extensions
  console.log('\n-> 2. Testing Extension Discovery across Repositories:');
  const extensions = await repoManager.getAllAvailableExtensions();
  console.log(`   Discovered ${extensions.length} extensions from repository.`);
  if (extensions.length === 0) {
    throw new Error('No extensions discovered from repository index');
  }
  for (const ext of extensions.slice(0, 3)) {
    console.log(`   - [${ext.lang.toUpperCase()}] ${ext.name} (v${ext.version}) | NSFW: ${ext.isNsfw}`);
  }
  console.log('   [PASS] Extension discovery works.');

  // 3. Install extension into Sandboxed Runtime
  console.log('\n-> 3. Testing Dynamic Extension Installation into Sandbox:');
  const targetExt = extensions[0];
  console.log(`   Installing "${targetExt.name}" into sandbox...`);
  const source = await repoManager.installExtension(targetExt);

  if (!repoManager.isInstalled(targetExt.id)) {
    throw new Error('Extension should be marked as installed');
  }

  const activeSources = repoManager.getAllActiveSources();
  console.log(`   Active sandboxed sources count: ${activeSources.length}`);
  if (activeSources.length !== 1) {
    throw new Error('Active sources count should be 1');
  }

  // 4. Test running chapters from installed extension
  console.log('   Testing chapter fetching from installed sandboxed source:');
  const chapters = await source.getChapters('test-manga-123');
  console.log(`   Retrieved ${chapters.length} chapters.`);
  if (chapters.length === 0) {
    throw new Error('Chapters should be returned from sandboxed extension');
  }

  // 5. Uninstall extension
  console.log('\n-> 4. Testing Extension Uninstallation:');
  repoManager.uninstallExtension(targetExt.id);
  if (repoManager.isInstalled(targetExt.id)) {
    throw new Error('Extension should be uninstalled');
  }
  if (repoManager.getAllActiveSources().length !== 0) {
    throw new Error('Active sources should be empty after uninstall');
  }
  console.log('   [PASS] Uninstall works cleanly.');

  console.log('\n=== ALL REPOSITORY MANAGER TESTS PASSED (%100) ===');
}

runRepositoryManagerTests().catch(err => {
  console.error('Test error:', err);
  process.exit(1);
});
