package com.littlepages.app.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val banglaCopy = mapOf(
    "Home" to "হোম", "Library" to "বইয়ের সংগ্রহ", "Community" to "কমিউনিটি", "Profile" to "প্রোফাইল",
    "A little world\nof stories" to "গল্পের ছোট্ট এক জগৎ", "A parent account for your family’s reading journey" to "পরিবারের পড়ার আনন্দের জন্য অভিভাবক অ্যাকাউন্ট",
    "Continue with Google" to "Google দিয়ে চালিয়ে যান", "Continue with email" to "ইমেইল দিয়ে চালিয়ে যান", "Continue with phone" to "ফোন নম্বর দিয়ে চালিয়ে যান",
    "Already have an account? " to "আগে থেকেই অ্যাকাউন্ট আছে? ", "Sign in" to "সাইন ইন", "Explore app preview" to "অ্যাপের নমুনা দেখুন",
    "By continuing, you agree to our Terms and Privacy Policy." to "এগিয়ে গেলে আপনি আমাদের ব্যবহারের শর্ত ও গোপনীয়তা নীতিতে সম্মতি দিচ্ছেন।",
    "Welcome back" to "আবার স্বাগতম", "Create parent account" to "অভিভাবক অ্যাকাউন্ট তৈরি করুন", "Use a parent email to manage your family’s reading." to "পরিবারের পড়া পরিচালনা করতে অভিভাবকের ইমেইল ব্যবহার করুন।",
    "Email address" to "ইমেইল ঠিকানা", "Password" to "পাসওয়ার্ড", "Confirm password" to "পাসওয়ার্ড নিশ্চিত করুন", "Forgot password?" to "পাসওয়ার্ড ভুলে গেছেন?",
    "Enter a valid email address." to "সঠিক ইমেইল ঠিকানা লিখুন।", "Use at least 8 characters." to "কমপক্ষে ৮টি অক্ষর ব্যবহার করুন।", "Passwords do not match." to "পাসওয়ার্ড দুটি মেলেনি।",
    "Create account" to "অ্যাকাউন্ট তৈরি করুন", "New to LittlePages? Create an account" to "LittlePages-এ নতুন? অ্যাকাউন্ট তৈরি করুন", "Already have an account? Sign in" to "আগে থেকেই অ্যাকাউন্ট আছে? সাইন ইন করুন",
    "Continue with phone" to "ফোন নম্বর দিয়ে চালিয়ে যান", "We’ll text a one-time code to verify your number." to "আপনার নম্বর যাচাই করতে একবার ব্যবহারযোগ্য কোড পাঠানো হবে।",
    "Phone number" to "ফোন নম্বর", "Enter a valid phone number." to "সঠিক ফোন নম্বর লিখুন।", "Send verification code" to "যাচাইকরণ কোড পাঠান",
    "We use your number only to secure your account." to "আপনার অ্যাকাউন্ট সুরক্ষিত রাখতে নম্বরটি ব্যবহার করা হবে।", "Use phone instead" to "ফোন নম্বর ব্যবহার করুন",
    "Good morning, Emma 👋" to "শুভ সকাল, Emma 👋", "Ready for a new adventure?" to "নতুন এক অভিযানের জন্য প্রস্তুত?", "Continue reading" to "পড়া চালিয়ে যান",
    "Popular this week" to "এই সপ্তাহের জনপ্রিয় বই", "See all" to "সব দেখুন", "Listen and read along" to "শুনুন ও একসঙ্গে পড়ুন", "Words light up as stories are read" to "গল্প পড়ার সময় শব্দগুলো আলোকিত হবে",
    "Your offline books" to "আপনার অফলাইন বই", "Book library" to "বইয়ের সংগ্রহ", "Find a story for every little mood." to "প্রতিটি মনের জন্য একটি গল্প খুঁজুন।", "Browse by age" to "বয়স অনুযায়ী খুঁজুন", "All stories" to "সব গল্প",
    "Search books" to "বই খুঁজুন", "Title, character, or topic" to "শিরোনাম, চরিত্র বা বিষয়", "Suggested for you" to "আপনার জন্য বাছাই করা", "Popular searches" to "জনপ্রিয় অনুসন্ধান",
    "Book details" to "বইয়ের বিবরণ", "Read in app" to "অ্যাপে পড়ুন", "Listen and read along · Premium" to "শুনুন ও পড়ুন · প্রিমিয়াম", "Choose a format" to "বইয়ের ধরন বাছুন",
    "PDF · Read in app" to "PDF · অ্যাপে পড়ুন", "Instant access · no PDF download" to "সঙ্গে সঙ্গে পড়ুন · PDF ডাউনলোড নয়", "Printed book" to "মুদ্রিত বই", "Order a book" to "বই অর্ডার করুন",
    "Close reader" to "পাঠ বন্ধ করুন", "Previous page" to "আগের পৃষ্ঠা", "Next page" to "পরের পৃষ্ঠা", "Read along" to "একসঙ্গে পড়ুন", "Autoplay pages" to "পৃষ্ঠা স্বয়ংক্রিয়ভাবে চালান",
    "Parent community" to "অভিভাবক কমিউনিটি", "A kind space to share the reading journey." to "পড়ার অভিজ্ঞতা ভাগ করে নেওয়ার সুন্দর একটি জায়গা।", "Recent posts" to "সাম্প্রতিক পোস্ট",
    "Create a post" to "পোস্ট লিখুন", "Preview post" to "পোস্টের নমুনা", "What would you like to share?" to "আপনি কী ভাগ করে নিতে চান?", "Photo selected" to "ছবি বাছাই করা হয়েছে",
    "Write a review" to "রিভিউ লিখুন", "Your rating" to "আপনার রেটিং", "Share your thoughts" to "আপনার মতামত লিখুন", "Save preview" to "নমুনা সংরক্ষণ করুন",
    "Available offline" to "অফলাইনে পাওয়া যাবে", "No offline books yet" to "এখনো কোনো অফলাইন বই নেই", "Browse books" to "বই দেখুন", "Order history is empty" to "অর্ডারের তালিকা খালি",
    "Parent account" to "অভিভাবক অ্যাকাউন্ট", "Family" to "পরিবার", "Your account" to "আপনার অ্যাকাউন্ট", "Child profiles" to "শিশুর প্রোফাইল", "LittlePages Premium" to "LittlePages প্রিমিয়াম",
    "Language" to "ভাষা", "Offline books" to "অফলাইন বই", "Orders" to "অর্ডার", "Help and support" to "সহায়তা",
    "Add a child profile" to "শিশুর প্রোফাইল যোগ করুন", "Edit child profile" to "শিশুর প্রোফাইল সম্পাদনা", "Nickname" to "ডাকনাম", "Age range" to "বয়স",
    "Save preview" to "নমুনা সংরক্ষণ করুন", "Cancel" to "বাতিল", "Not available yet" to "এখনো চালু হয়নি", "OK" to "ঠিক আছে",
    "Help and support" to "সহায়তা", "Your email" to "আপনার ইমেইল", "How can we help?" to "কীভাবে সাহায্য করতে পারি?", "Send support request" to "সহায়তার অনুরোধ পাঠান",
    "Choose a format" to "বইয়ের ধরন বাছুন", "Delivery address" to "ডেলিভারির ঠিকানা", "Full name" to "পুরো নাম", "Address and area" to "ঠিকানা ও এলাকা",
    "Payment" to "পেমেন্ট", "Continue to payment" to "পেমেন্টে এগিয়ে যান", "Narration settings" to "পাঠের শব্দ সেটিংস", "Voice" to "কণ্ঠ", "Reading speed" to "পড়ার গতি", "Done" to "সম্পন্ন"
    ,"The Brave Little Fox · 8 of 24 pages" to "সাহসী ছোট্ট শিয়াল · ২৪টির মধ্যে ৮ পৃষ্ঠা"
    ,"Open" to "খুলুন", "You’re doing great!" to "তুমি খুব ভালো করছ!", "34% complete" to "৩৪% সম্পন্ন"
    ,"Title, character, or topic" to "শিরোনাম, চরিত্র বা বিষয়", "A story about kindness, courage, and finding your voice. Follow a curious little fox as a new adventure leads to an unexpected friendship." to "দয়া, সাহস আর নিজের কণ্ঠ খুঁজে পাওয়ার গল্প। এক কৌতূহলী ছোট্ট শিয়াল নতুন অভিযানে গিয়ে এক বন্ধুকে খুঁজে পায়।"
    ,"Available · delivery in Bangladesh" to "পাওয়া যাচ্ছে · বাংলাদেশে ডেলিভারি", "“A lovely story my child asks for every night.”" to "“আমার শিশু প্রতি রাতে এই সুন্দর গল্পটি শুনতে চায়।”"
    ," Listen" to " শুনুন", "The little fox " to "ছোট্ট শিয়ালটি ", "listened" to "মন দিয়ে শুনল", " to the birds’ cheerful song." to " পাখিদের মিষ্টি গান।"
    ,"Try a one-page read-along sample" to "এক পৃষ্ঠার পড়ে শোনার নমুনা শুনুন", "One-page read-along preview" to "এক পৃষ্ঠার পড়ে শোনার নমুনা"
    ,"Continue to the next page automatically" to "পরের পৃষ্ঠায় স্বয়ংক্রিয়ভাবে যান", "Warm storyteller · English" to "স্নিগ্ধ গল্পকথক · বাংলা"
    ," Share a thought with parents…" to "অভিভাবকদের সঙ্গে ভাবনা ভাগ করুন…", "312 parents · Stories and ideas for caring families" to "৩১২ জন অভিভাবক · যত্নশীল পরিবারের গল্প ও ভাবনা", "Joined" to "যোগ দিয়েছেন", "Write a reply…" to "উত্তর লিখুন…"
    ,"Emma’s parent" to "Emma-র অভিভাবক", "Posting as a parent" to "অভিভাবক হিসেবে পোস্ট করছেন", "What would you like to share?" to "আপনি কী ভাগ করে নিতে চান?"
    ,"Attached to this local preview" to "এই স্থানীয় নমুনায় যুক্ত হয়েছে", "Please keep children’s personal details private. Posts are visible to community members." to "শিশুর ব্যক্তিগত তথ্য গোপন রাখুন। পোস্টগুলো কমিউনিটির সদস্যরা দেখতে পারবেন।"
    ,"Posts are only kept for this app session until the community service is configured." to "কমিউনিটি সেবা চালু না হওয়া পর্যন্ত পোস্ট শুধু এই অ্যাপ সেশনে থাকবে।"
    ,"Based on 1,240 parent reviews" to "১,২৪০ জন অভিভাবকের রিভিউ", "You · preview only" to "আপনি · শুধু নমুনা", "Report" to "রিপোর্ট করুন"
    ,"Report review" to "রিভিউ রিপোর্ট করুন", "Reports are not sent until the community moderation service is configured." to "কমিউনিটি মডারেশন চালু না হওয়া পর্যন্ত রিপোর্ট পাঠানো হবে না।", "Got it" to "বুঝেছি"
    ,"No book files are stored on this device by the current preview." to "বর্তমান নমুনায় এই ডিভাইসে কোনো বইয়ের ফাইল সংরক্ষিত নেই।"
    ,"Preview profiles are held in memory only; account sync and consent settings are not configured." to "নমুনা প্রোফাইল শুধু মেমরিতে থাকে; অ্যাকাউন্ট সিঙ্ক ও সম্মতির সেটিংস চালু নেই।"
    ,"Parent account controls purchases, subscriptions, and community posts." to "অভিভাবক অ্যাকাউন্ট থেকে কেনাকাটা, সাবস্ক্রিপশন ও কমিউনিটি পোস্ট নিয়ন্ত্রণ করা হয়।"
    ,"Stories come alive" to "গল্প হয়ে উঠুক জীবন্ত", "A little extra magic for family reading time." to "পরিবারের পড়ার সময়ে যোগ হোক একটু বাড়তি জাদু।"
    ,"Word-by-word narrated read-along" to "শব্দে শব্দে পড়া ও শোনা", "Warm voices and adjustable speed" to "স্নিগ্ধ কণ্ঠ ও গতি নিয়ন্ত্রণ", "More stories for your family" to "পরিবারের জন্য আরও গল্প", "Offline listening on supported books" to "সমর্থিত বই অফলাইনে শুনুন"
    ,"Family plan" to "পরিবারের প্ল্যান", "Billed monthly · cancel anytime" to "মাসিক বিল · যেকোনো সময় বাতিল করুন"
    ,"Subscriptions are managed securely through the available payment option." to "সাবস্ক্রিপশন নিরাপদে পেমেন্ট সেবার মাধ্যমে পরিচালিত হবে।"
    ,"Choose how your family will read" to "আপনারা কীভাবে পড়বেন তা বেছে নিন"
    ,"Tell us what happened and our team will help." to "সমস্যাটি জানান, আমাদের দল সাহায্য করবে।", "This preview does not send or store support messages." to "এই নমুনা সহায়তার বার্তা পাঠায় বা সংরক্ষণ করে না।"
    ,"Make story time magical" to "গল্পের সময়কে জাদুময় করুন", "Listen to the story with each word lighting up as it’s read. Premium is managed by a parent." to "গল্প শুনুন, পড়ার সঙ্গে প্রতিটি শব্দ আলোকিত হবে। প্রিমিয়াম অভিভাবক পরিচালনা করবেন।"
    ,"Not now" to "এখন নয়", "Explore Premium" to "প্রিমিয়াম দেখুন"
    ,"Search books, characters, or topics" to "বই, চরিত্র বা বিষয় খুঁজুন", "  or  " to "  অথবা  ", "Code" to "কোড", "Post" to "পোস্ট", "Share" to "শেয়ার করুন"
    ,"  Search books, characters, or topics" to "  বই, চরিত্র বা বিষয় খুঁজুন", "Join group →" to "গ্রুপে যোগ দিন →", "Years" to "বছর", "LittlePages" to "লিটলপেজেস"
    ,"2 stories ready for a trip" to "অফলাইন বই সংরক্ষণ এখনো চালু হয়নি", "Offline book storage isn't set up yet" to "অফলাইন বই সংরক্ষণ এখনো চালু হয়নি"
    ,"The Brave Little Fox" to "সাহসী ছোট্ট শিয়াল", "A Day at the Lake" to "হ্রদের ধারে একদিন", "The Kind Giant" to "দয়ালু দৈত্য", "The Star Keeper" to "তারাদের রক্ষক"
    ,"The little fox looked out across the valley. A brand new day was waiting, full of big possibilities." to "ছোট্ট শিয়ালটি উপত্যকার দিকে তাকাল। নতুন একটি দিন তার জন্য অপেক্ষা করছিল, ভরা ছিল বড় বড় সম্ভাবনায়।"
    ,"The path curled between the trees and down toward the shining river. Fox listened to the birds’ cheerful song." to "গাছের ফাঁক দিয়ে পথটি ঝলমলে নদীর দিকে নেমে গেছে। শিয়াল পাখিদের আনন্দের গান শুনল।"
    ,"A tiny blue bird fluttered close. ‘I’m looking for my family,’ she said. Fox smiled and offered to help." to "একটি ছোট নীল পাখি কাছে উড়ে এল। সে বলল, ‘আমি আমার পরিবারকে খুঁজছি।’ শিয়াল হেসে তাকে সাহায্য করতে চাইল।"
    ,"Together they followed the river until they heard a happy chorus from the willow trees." to "তারা একসঙ্গে নদীর ধারে চলল, যতক্ষণ না উইলো গাছ থেকে আনন্দের কোরাস শুনতে পেল।"
    ,"The bird found her family, and Fox discovered that a kind adventure is even better when shared." to "পাখিটি তার পরিবারকে খুঁজে পেল। শিয়াল বুঝল, সুন্দর কোনো অভিযান ভাগ করে নিলে আরও আনন্দ হয়।"
    ,"    ◯  8 comments" to "    ◯  ৮টি মন্তব্য", " LittlePages" to " লিটলপেজেস", "🌱  Raising Kind Kids" to "🌱  সদয় সন্তান গড়ে তুলি"
    ,"All" to "সব", "Animals" to "প্রাণী", "Friendship" to "বন্ধুত্ব", "Nature" to "প্রকৃতি", "Bedtime" to "ঘুমের গল্প", "Bangla" to "বাংলা"
    ,"All ages" to "সব বয়স", "Ages 0–3" to "০–৩ বছর", "Ages 4–6" to "৪–৬ বছর", "Ages 7–9" to "৭–৯ বছর"
    ,"No books match these filters" to "এই বাছাইয়ে কোনো বই নেই", "Try another category or age range." to "অন্য বিষয় বা বয়স বেছে দেখুন।", "No books found" to "কোনো বই পাওয়া যায়নি"
    ,"Try a title, author, category, or another search." to "শিরোনাম, লেখক বা বিষয় দিয়ে আবার খুঁজুন।", "Kindness" to "দয়া"
)

private fun banglaDigits(value: String): String = value.map { character -> if (character in '0'..'9') '০' + (character - '0') else character }.joinToString("")

@Composable
internal fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    style: TextStyle = LocalTextStyle.current
) {
    val isBangla = LocalConfiguration.current.locales[0].language == "bn"
    val renderedText = if (!isBangla) text else when {
        text.startsWith("Selected: ") -> "নির্বাচিত: ${text.substringAfter("Selected: ").substringBefore(". Payment")}। পেমেন্ট এখনো চালু হয়নি। PDF শুধু অ্যাপেই পড়া যাবে।"
        text.startsWith("The Brave Little Fox · ") && text.contains(" of 24 pages") -> "সাহসী ছোট্ট শিয়াল · ২৪টির মধ্যে ${banglaDigits(text.substringAfter("· ").substringBefore(" of"))} পৃষ্ঠা"
        text.endsWith("% complete") -> "${banglaDigits(text.substringBefore('%'))}% সম্পন্ন"
        text.startsWith("Page ") -> "পৃষ্ঠা ${banglaDigits(text.substringAfter("Page "))}"
        else -> banglaCopy[text] ?: text
    }
    MaterialText(
        text = renderedText,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        lineHeight = lineHeight,
        maxLines = maxLines,
        minLines = minLines,
        overflow = overflow,
        style = style
    )
}
