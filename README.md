# App Fusion (MVP)

App Fusion is an Android workflow app prototype. The first milestone accepts a PDF from Android's Sharesheet or system document picker, extracts selectable text locally, lets you clean or summarize it, and saves or copies the result on request.

## Environment and build

The development environment used was Termux on Android 12 (API 31), arm64, with OpenJDK 21 and Git. No Gradle installation or Android SDK/Build Tools were present, and `adb devices` found no attached device. Therefore no APK has been built or installed in this environment.

The project is configured for Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 17+, Android SDK 36, Kotlin 2.2.21 and Compose. To build from a Linux/Termux environment with Android SDK installed:

1. Install JDK 17 or newer, Gradle 8.13, Android SDK platform 36 and Build Tools 35.0.0.
2. From this directory run `gradle assembleDebug testDebugUnitTest`.
3. The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.
4. Install over USB debugging or with local ADB using `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

A debug APK is not included because the required SDK and Gradle are missing here. A GitHub Actions workflow is included to build and publish the APK as a workflow artifact once the repository is pushed to GitHub.

## Try the MVP

- Launch App Fusion and choose a PDF, or share a PDF from another app and select App Fusion.
- Wait for local extraction, then choose **Clean text** or **Short summary**.
- Choose **Save result** to pick an output location, or **Copy** to explicitly copy the result.
- Pasted or shared text can use the same local operations.

## Workflow data model

`WorkflowDefinition` contains a stable id, display name and ordered list of `WorkflowStep` values. Each step refers to the closed `Operation` enum; the validator checks the sequence of typed inputs/outputs before execution. `WorkflowEngine` passes each step's output to the next and returns a `WorkflowRun` status, results and actionable error. The model is in `app/src/main/java/com/appfusion/domain/Workflow.kt`.

Supported operations in this milestone: accept text, extract text from a PDF URI, clean whitespace, produce a simple first-three-sentences summary, and display the result. Saving is a separate explicit Android Storage Access Framework action after processing. There is no arbitrary code execution and no AI provider configured. The local summary is heuristic, not an AI-generated summary. Translation, OCR, multi-file input, reusable user-authored workflows and provider integrations are not available yet.

## Privacy and limitations

- PDF access is granted through the shared URI or Android document picker; the app does not request broad storage permission.
- PDF text extraction and cleanup/summary happen locally. No network permission is declared and no document content is uploaded.
- Recent history stores operation labels and timestamps only, not document text; use **Clear** to remove it.
- Password-protected PDFs, malformed PDFs, scanned/image-only PDFs and documents above 200 pages are rejected with an explanation. PDF input is limited to 50 MB and 200 pages; extracted text is capped at 100,000 characters.
- The app does not use Accessibility Services, root access, clipboard monitoring, or background file access.
- This is the first functional milestone, not unrestricted cross-app automation. Only Android's supported share contract is integrated.

## Verification

Unit tests cover workflow typing/validation, execution order, data handoff and failure reporting. The tests have not been run because Gradle and the Android SDK are unavailable in the current environment. To run them, execute `gradle testDebugUnitTest` from the project root on a configured build host. Android instrumentation tests were not added; the app can be exercised on an Android 8.0+ device by installing the generated debug APK and sharing a selectable-text PDF to App Fusion.
