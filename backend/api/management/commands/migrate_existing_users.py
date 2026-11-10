
from pathlib import Path

from django.core.management.base import BaseCommand, CommandError
from google.cloud import firestore
from google.api_core.exceptions import AlreadyExists


class Command(BaseCommand):
    help = "既存ユーザーを診断必須化の対象外として登録する"

    def add_arguments(self, parser):
        parser.add_argument(
            "--uids-file",
            required=True,
            help="既存ユーザーUIDを1行ずつ記載したファイル",
        )
        parser.add_argument(
            "--apply",
            action="store_true",
            help="指定時のみFirestoreに書き込む",
        )

    def handle(self, *args, **options):
        path = Path(options["uids_file"])

        if not path.is_file():
            raise CommandError("UID file not found")

        uids = [
            line.strip()
            for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip()
        ]

        if len(uids) != len(set(uids)):
            raise CommandError("Duplicate UID found")

        if not uids:
            raise CommandError("UID list is empty")

        db = firestore.Client()

        created = 0
        skipped = 0

        for uid in uids:
            if "/" in uid or uid in (".", ".."):
                raise CommandError("Invalid UID")

            profile_ref = db.collection("users").document(uid)
            policy_ref = (
                db.collection("registrationPolicies")
                .document(uid)
            )

            profile = profile_ref.get()
            policy = policy_ref.get()

            if not profile.exists:
                self.stdout.write(f"SKIP no profile: {uid}")
                skipped += 1
                continue

            if policy.exists:
                self.stdout.write(f"SKIP policy exists: {uid}")
                skipped += 1
                continue

            if not options["apply"]:
                self.stdout.write(f"DRY RUN Existing: {uid}")
                continue

            try:
                policy_ref.create({
                    "policyVersion": 1,
                    "diagnosisRequired": False,
                    "registrationState": "Existing",
                    "verifiedDiagnosisId": None,
                    "createdAt": firestore.SERVER_TIMESTAMP,
                })
                created += 1
                self.stdout.write(f"CREATED Existing: {uid}")

            except AlreadyExists:
                skipped += 1
                self.stdout.write(f"SKIP concurrent policy: {uid}")

        self.stdout.write(
            self.style.SUCCESS(
                f"Done: created={created}, skipped={skipped}"
            )
        )
