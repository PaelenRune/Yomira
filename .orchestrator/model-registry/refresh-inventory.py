"""Capture model inventory evidence; never infer successful model invocation."""

import collections
import datetime
import hashlib
import json
from pathlib import Path
import re
import subprocess

from pypdf import PdfReader


ROOT = Path(__file__).resolve().parents[1]
REGISTRY = ROOT / "model-registry"
SOURCE = Path(r"C:\Users\paele\Desktop\OpenChamber_Aktif_Modeller_Listesi.pdf")
MODEL_PATTERN = r"(?:google|evren[23]?|nvidia|openai|opencode)/[a-zA-Z0-9._/-]+"
EXCLUSION = r"gemini[-_. ]?3[._-]?8[-_. ]?flash"


def save(name, value):
    (REGISTRY / name).write_text(
        json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def main():
    REGISTRY.mkdir(parents=True, exist_ok=True)
    (ROOT / "project-management").mkdir(parents=True, exist_ok=True)
    now = datetime.datetime.now(datetime.timezone.utc).isoformat()
    reader = PdfReader(SOURCE)
    text = "\n".join(page.extract_text() or "" for page in reader.pages)
    pdf_models = sorted(set(re.findall(MODEL_PATTERN, text)))
    cli = subprocess.run(["opencode", "models"], capture_output=True, text=True, check=True)
    cli_models = sorted(set(line.strip() for line in cli.stdout.splitlines()
                            if re.fullmatch(MODEL_PATTERN, line.strip())))
    assert len(pdf_models) == 90, "Unexpected PDF extraction count"
    assert cli_models, "CLI returned no model IDs"
    excluded = sorted(model for model in set(pdf_models + cli_models)
                      if re.search(EXCLUSION, model, re.IGNORECASE))
    eligible = sorted(set(pdf_models).intersection(cli_models).difference(excluded))
    (REGISTRY / "inventory-pdf-extracted.txt").write_text(text, encoding="utf-8")
    save("inventory-snapshot-2026-10-27.json", {
        "schemaVersion": "1.0.0", "generatedAt": now,
        "source": {"requestedFilename": "OpenChamber_Aktif_Modeller_Listesi(1).pdf",
                   "actualPath": str(SOURCE), "requestedExactFileFound": False,
                   "sha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                   "pages": len(reader.pages), "reportDate": "2026-10-20"},
        "verification": "CLI catalog presence only; no inference/auth/quota test performed",
        "pdf": {"count": len(pdf_models), "models": pdf_models,
                "providerCounts": dict(collections.Counter(m.split('/')[0] for m in pdf_models))},
        "cli": {"command": "opencode models", "exitCode": cli.returncode,
                "count": len(cli_models), "models": cli_models,
                "providerCounts": dict(collections.Counter(m.split('/')[0] for m in cli_models))},
        "pdfModelsAbsentFromCli": sorted(set(pdf_models).difference(cli_models)),
        "cliModelsNotInPdf": sorted(set(cli_models).difference(pdf_models)),
        "excludedOrchestratorAliases": excluded,
        "pdfAndCliNonExcludedCount": len(eligible),
    })
    save("subagent-selection-policy.json", {
        "schemaVersion": "1.0.0", "generatedAt": now,
        "orchestratorModel": "google/antigravity-gemini-3.8-flash",
        "denyModelIdRegex": EXCLUSION, "caseInsensitive": True,
        "deniedKnownModelIds": excluded,
        "policy": "Deny matching aliases across every provider before selecting a subagent.",
        "catalogOnlyModelsAreNotAutomaticallyEligible": True,
        "candidateSource": "Intersection of dated active PDF inventory and current CLI catalog",
        "requiresTaskCapabilityAndAccessVerification": True,
        "nonExcludedInventoryCandidates": eligible,
        "note": "Image/embedding-only entries require specialist tasks, not generic coding agents. Existing historical registry files are preserved, not selection allowlists.",
    })
    print(json.dumps({"pdf": len(pdf_models), "cli": len(cli_models),
                      "eligibleInventoryCandidates": len(eligible), "excluded": excluded,
                      "pdfModelsAbsentFromCli": sorted(set(pdf_models).difference(cli_models))}, indent=2))


if __name__ == "__main__":
    main()
