# Root convenience targets. Heavy verification belongs on GitHub runners; these
# targets exist so the operator can do the last 10% on their own machine.

.PHONY: bench-device lint-kt test-arch

bench-device: ## Run a single-device decode-rate benchmark on the attached phone
	./scripts/bench-device.sh

lint-kt: ## Run the Kotlin structural lints over androidTest + main
	python3 .github/scripts/check_kt_braces.py android/app/src/androidTest/java/com/grapsee/gsai/*.kt android/app/src/main/java/com/grapsee/gsai/native/*.kt

test-arch: ## Print the iOS test arch prediction (measured answer is in ISSUE-LOG)
	@echo "iOS test arch is reported by the iOS-EXEC-ARCH line in the XCTest job log; see native/docs/ISSUE-LOG.md"
