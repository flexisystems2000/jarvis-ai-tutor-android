# JARVIS AI Tutor — Native Android starter

A native Android application starter built with Kotlin and Jetpack Compose for Flexi Digital Academy.

## Included in this ZIP

- Native Android app foundation (Kotlin + Jetpack Compose).
- Navigation drawer with Tutor, CBT Practice, History, Settings and Plans.
- Personalization settings: tutor name, app language, theme and web-search preference.
- Plan and entitlement policy:
  - Free: 50 tokens per day.
  - Jarvis Plus: 4,000 tokens; longer chats, more image creation and more CBT practice.
  - Jarvis Pro: 10,000 tokens; Plus benefits and video creation.
  - Flexi Masterclass: no token limit or upgrade prompts; paid features included.
- Backend interfaces for AI tutoring, web search, authentication and payment checkout.
- Unit tests for the token/entitlement rules.
- GitHub Actions workflow to build a debug APK and run unit tests.

## Important status

This is the complete **starter project structure**, not a finished production service. The screens currently demonstrate the app flow. AI responses, real web search, Google/email sign-in, Flexi Masterclass identity linking, cloud conversation sync, CBT question banks, image/video generation, and payment processing still need their secure backend integrations.

The plan selector in this starter is preview-only. It does not charge a card or grant a paid entitlement. Production entitlements must be issued by the backend only after verified payment or verified Flexi Masterclass account linking.

## Security requirements

- Never put Supabase service-role keys, AI provider keys, payment secret keys, or Firebase Admin credentials in the APK.
- Validate token usage and plan entitlements on the backend for every billable request.
- Flexi Masterclass account linking must verify the existing account with the Flexi identity system. Do not treat a phone number or registration ID alone as proof of identity.
- Create payment sessions on the backend and verify payment server-to-server before upgrading a plan.

## Build

The GitHub Actions workflow installs Java 17, Android SDK and Gradle 8.11.1, then runs:

```bash
gradle assembleDebug
gradle testDebugUnitTest
```

After pushing to the `main` branch, download the `jarvis-ai-tutor-debug-apk` artifact from the successful GitHub Actions run.

## Next implementation milestones

1. Connect Supabase Auth and database using a secure backend.
2. Link Flexi Masterclass login to the existing account system.
3. Add server-side token ledger, daily reset, usage metering and plan enforcement.
4. Connect AI tutor, image generation and web search APIs.
5. Add CBT question delivery, timer, navigation, submission, marking and explanations.
6. Integrate weekly mock results from the existing Flexi Lessons data source.
7. Add payment gateway checkout and server-side webhook verification.
8. Add video generation for Pro and Flexi Masterclass accounts.
9. Add DataStore persistence, localization resources and production UI polish.
