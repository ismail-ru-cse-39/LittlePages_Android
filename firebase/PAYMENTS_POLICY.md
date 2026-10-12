# Google Play billing policy review

**Reviewed:** 2026-10-12  
**Launch market in scope:** Bangladesh  
**Status:** Policy research only. This is not legal advice or a substitute for checking the app's Play Console enrollment and current policy before release.

## Digital books and Premium

Google Play's Payments policy says Play-distributed apps must use Google Play Billing for in-app purchases that unlock digital content, app functionality, or subscriptions, except where a listed exception or enrolled regional program applies. A Premium subscription that unlocks narration or other in-app digital features, and a one-time purchase that unlocks a digital book/PDF read in the app, fall within those examples.

The policy's physical-goods exception applies when payment is primarily for a physical product. A SKU that includes both a digital book and a printed book needs classification based on what it includes and how it is marketed; the policy says to use Play Billing for SKUs containing more digital value than physical value or marketed as digital. Keep digital access and physical-only products as separate purchase options/SKUs unless Play confirms a compliant bundled configuration.

Bangladesh is listed as supporting Play developer and merchant registration. Bangladesh is not among the currently listed markets for Google's user-choice or alternative billing programs. Therefore the planned Play-distributed Android app should use Google Play Billing for digital books and Premium in Bangladesh. bKash/Visa may be considered for physical-only orders, not as a substitute digital checkout inside the app. Do not link from digital purchase prompts to an outside checkout unless the app is enrolled in a program that permits it in the user's market.

## Subscription presentation

Before a purchase, disclose the localized price, billing frequency, automatic renewal, subscription content, and how to manage or cancel. Include a clear route to subscription management in account settings. Do not imply that Premium is required for catalogue items that are available without it.

## Implementation implications

- Keep Play subscription and one-time digital products in Play Billing and verify purchase tokens on a trusted server before granting access.
- Use a separate physical-only checkout and provider flow for printed books; collect confirmed stock and delivery quote before accepting the order.
- Treat refunds, cancellations, renewals, and chargebacks as trusted server events that can update entitlements.
- Recheck the policy, market program eligibility, and Play Console product setup immediately before implementation/release; program coverage and requirements can change.

## Sources

- [Google Play Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738?hl=en): digital in-app purchases, subscription examples, physical-goods exception, and regional program exceptions.
- [Understanding Google Play's Payments policy](https://support.google.com/googleplay/android-developer/answer/10281818?hl=en-en): digital content and subscription examples, consumption-only apps, and mixed digital/physical SKUs.
- [Supported locations for developer and merchant registration](https://support.google.com/googleplay/android-developer/answer/9306917?hl=en): Bangladesh supports developer and merchant registration.
- [Understanding user choice billing](https://support.google.com/googleplay/android-developer/answer/13821247): eligible alternative billing markets and enrollment requirements; Bangladesh is not listed.
- [Google Play subscription policy](https://support.google.com/googleplay/android-developer/answer/9900533?hl=en): clear subscription terms, pricing, renewal, and cancellation requirements.
