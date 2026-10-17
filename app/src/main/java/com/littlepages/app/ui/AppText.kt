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
    "Already have an account? " to "আগে থেকেই অ্যাকাউন্ট আছে? ", "Sign in" to "সাইন ইন", "Continue as guest" to "অতিথি হিসেবে চালিয়ে যান",
    "Guest access" to "অতিথি প্রবেশ", "Browsing without an account" to "অ্যাকাউন্ট ছাড়া ব্রাউজ করছেন", "Create a parent account" to "অভিভাবক অ্যাকাউন্ট তৈরি করুন", "Exit guest mode" to "অতিথি মোড থেকে বের হন",
    "Create an account to save your reading progress across devices, order books, and join the parent community." to "বিভিন্ন ডিভাইসে পড়ার অগ্রগতি সংরক্ষণ, বই অর্ডার এবং অভিভাবক কমিউনিটিতে যোগ দিতে অ্যাকাউন্ট তৈরি করুন।",
    "You're offline. Only locally available content can be opened." to "আপনি অফলাইনে আছেন। শুধু ডিভাইসে থাকা কনটেন্ট খোলা যাবে।",
    "By continuing, you agree to our Terms and Privacy Policy." to "এগিয়ে গেলে আপনি আমাদের ব্যবহারের শর্ত ও গোপনীয়তা নীতিতে সম্মতি দিচ্ছেন।",
    "Welcome back" to "আবার স্বাগতম", "Create parent account" to "অভিভাবক অ্যাকাউন্ট তৈরি করুন", "Use a parent email to manage your family’s reading." to "পরিবারের পড়া পরিচালনা করতে অভিভাবকের ইমেইল ব্যবহার করুন।",
    "Verify your email" to "আপনার ইমেইল যাচাই করুন", "I’ve verified" to "আমি যাচাই করেছি", "Resend verification email" to "যাচাইকরণ ইমেইল আবার পাঠান", "Verification email sent" to "যাচাইকরণ ইমেইল পাঠানো হয়েছে",
    "Email not verified yet" to "ইমেইল এখনো যাচাই হয়নি", "Parent email or phone" to "অভিভাবকের ইমেইল বা ফোন", "Text me a code" to "কোড পাঠান", "Verify and continue" to "যাচাই করে এগিয়ে যান", "Resend code" to "কোড আবার পাঠান",
    "Enter a valid Bangladesh mobile number." to "সঠিক বাংলাদেশি মোবাইল নম্বর লিখুন।", "Firebase may verify this device automatically. Otherwise, enter the code from your SMS." to "Firebase স্বয়ংক্রিয়ভাবে এই ডিভাইস যাচাই করতে পারে। না হলে SMS-এর কোড লিখুন।",
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
    ,"🌿     🐰📖🐰     🌼" to "🌿     🐰📖🐰     🌼", "Explore parent features · preview" to "অভিভাবকদের সুবিধাগুলো দেখুন · নমুনা"
    ,"Verification code" to "যাচাইকরণ কোড", "Enter all 6 digits." to "৬টি সংখ্যাই লিখুন।", "Change phone number" to "ফোন নম্বর পরিবর্তন করুন"
    ,"No SMS was sent. Verification will be available after Firebase Authentication is configured." to "কোনো SMS পাঠানো হয়নি। Firebase Authentication চালু হলে নম্বর যাচাই করা যাবে।"
    ,"Create a parent profile and add a child reader." to "অভিভাবকের প্রোফাইল তৈরি করে শিশুর পাঠক প্রোফাইল যোগ করুন।", "Parent name" to "অভিভাবকের নাম", "Parent email" to "অভিভাবকের ইমেইল", "Child nickname" to "শিশুর ডাকনাম"
    ,"This preview does not create an account or send your information. Connect authentication and parent consent before collecting real family details." to "এই নমুনায় অ্যাকাউন্ট তৈরি বা তথ্য পাঠানো হয় না। পরিবারের আসল তথ্য নেওয়ার আগে সাইন-ইন ও অভিভাবকের সম্মতি চালু করুন।"
    ,"৳120" to "৳১২০", "৳450" to "৳৪৫০", "Trouble opening this book?" to "বইটি খুলতে সমস্যা হচ্ছে?", "🦊" to "🦊", "🦊   🐦" to "🦊   🐦"
    ,"Preparing your device’s voice…" to "ডিভাইসের কণ্ঠ প্রস্তুত হচ্ছে…", "System default" to "সিস্টেমের ডিফল্ট কণ্ঠ"
    ,"Community guidelines: protect children’s privacy, be kind, and share only images you have permission to post. Report content that feels unsafe. Moderation is not connected in this preview." to "কমিউনিটির নিয়ম: শিশুর গোপনীয়তা রক্ষা করুন, সদয় থাকুন এবং অনুমতি থাকা ছবিই শেয়ার করুন। অনিরাপদ মনে হলে রিপোর্ট করুন। এই নমুনায় মডারেশন চালু নেই।"
    ,"Community guidelines" to "কমিউনিটির নিয়ম", "Keep children’s names, schools, locations, and contact details private. Treat other families with kindness. Share only images you have permission to post, and report unsafe or inappropriate content. Reports and moderation are preview-only until the community service is connected." to "শিশুর নাম, স্কুল, অবস্থান ও যোগাযোগের তথ্য গোপন রাখুন। অন্য পরিবারগুলোর সঙ্গে সদয় আচরণ করুন। অনুমতি থাকা ছবিই শেয়ার করুন এবং অনিরাপদ বা অনুপযুক্ত কনটেন্ট রিপোর্ট করুন। কমিউনিটি সেবা চালু না হওয়া পর্যন্ত রিপোর্ট ও মডারেশন শুধু নমুনা হিসেবে থাকবে।"
    ,"Talking about kindness" to "দয়া নিয়ে কথা বলা", "A short family conversation guide" to "পরিবারে কথা বলার ছোট নির্দেশিকা", "Bedtime reading routine" to "ঘুমের আগে পড়ার অভ্যাস", "Ideas shared by group parents" to "গ্রুপের অভিভাবকদের পরামর্শ"
    ,"Image not accepted" to "ছবিটি গ্রহণ করা যায়নি", "Choose another" to "অন্য ছবি বাছুন", "4.9" to "৪.৯", "★★★★★" to "★★★★★", "★" to "★", "Block" to "ব্লক করুন"
    ,"This preview will hide this person’s reviews on this screen. Blocking is not saved or sent to a moderation service." to "এই নমুনায় এই ব্যক্তির রিভিউ এই পর্দায় লুকানো হবে। ব্লক করার তথ্য সংরক্ষণ বা মডারেশন সেবায় পাঠানো হবে না।"
    ,"Storage and sync preview" to "সংরক্ষণ ও সিঙ্কের নমুনা", "These are sample list entries only. No book files are stored on this device, and real storage usage is not available yet." to "এগুলো শুধু নমুনা তালিকা। এই ডিভাইসে কোনো বইয়ের ফাইল রাখা হয়নি, তাই প্রকৃত স্টোরেজ ব্যবহারের তথ্য এখনো নেই।"
    ,"Sync status · Not connected" to "সিঙ্কের অবস্থা · সংযুক্ত নয়", "Retry sync" to "আবার সিঙ্ক করুন", "Preview entry · file not stored" to "নমুনা তালিকা · ফাইল সংরক্ষিত নয়", "Remove this book?" to "এই বইটি সরাবেন?", "Remove" to "সরান"
    ,"Final privacy wording, retention periods, and consent requirements need approval before launch." to "চালুর আগে গোপনীয়তার চূড়ান্ত ভাষা, তথ্য সংরক্ষণের সময়সীমা ও সম্মতির নিয়ম অনুমোদন করতে হবে।"
    ,"This preview does not send or store privacy requests. The account and consent service is not configured." to "এই নমুনা গোপনীয়তার অনুরোধ পাঠায় বা সংরক্ষণ করে না। অ্যাকাউন্ট ও সম্মতি সেবা কনফিগার করা হয়নি।"
    ,"✨" to "✨", "Sample billing terms · not final" to "বিলিংয়ের নমুনা · চূড়ান্ত নয়", "৳399 / mo · preview" to "৳৩৯৯ / মাস · নমুনা"
    ,"Choose a sample state to review the interface. This does not change a subscription or payment." to "পর্দাটি দেখার জন্য একটি নমুনা অবস্থা বাছুন। এতে সাবস্ক্রিপশন বা পেমেন্ট বদলাবে না।"
    ,"Billing, renewal, cancellation, and entitlements are not connected in this prototype." to "এই নমুনায় বিলিং, নবায়ন, বাতিল বা অ্যাক্সেস-সুবিধা সংযুক্ত নেই।"
    ,"Preview only: no payment is taken and no order is sent. PDFs remain in-app only." to "শুধু নমুনা: কোনো পেমেন্ট নেওয়া বা অর্ডার পাঠানো হয়নি। PDF শুধু অ্যাপেই পড়া যাবে।"
    ,"Preview payment recovery" to "পেমেন্ট সমস্যার নমুনা", "Preview only · no charge or fulfillment" to "শুধু নমুনা · কোনো চার্জ বা ডেলিভারি নেই"
    ,"If this continues, contact support from Parent Profile." to "সমস্যাটি চলতে থাকলে অভিভাবক প্রোফাইল থেকে সহায়তায় যোগাযোগ করুন।"
    ,"🎧✨" to "🎧✨", "🎧" to "🎧"
    ,"Try a title, author, category, or another search." to "শিরোনাম, লেখক বা বিষয় দিয়ে আবার খুঁজুন।", "Kindness" to "দয়া"
    ,"Back" to "পেছনে", "Post options" to "পোস্টের বিকল্প", "Unlike post" to "পোস্টে লাইক সরান", "Like post" to "পোস্টে লাইক দিন"
    ,"Photo shared by a parent" to "একজন অভিভাবকের শেয়ার করা ছবি", "Hide password" to "পাসওয়ার্ড লুকান", "Show password" to "পাসওয়ার্ড দেখান"
    ,"Clear search" to "অনুসন্ধান মুছুন", "Remove saved book" to "সংরক্ষিত বই সরান", "Save book" to "বই সংরক্ষণ করুন"
    ,"Back 10 seconds" to "১০ সেকেন্ড পেছনে", "Forward 10 seconds" to "১০ সেকেন্ড সামনে", "Pause narration" to "পড়া থামান", "Play narration" to "পড়া চালান"
    ,"Report post" to "পোস্ট রিপোর্ট করুন", "Block this parent" to "এই অভিভাবককে ব্লক করুন", "8 comments" to "৮টি মন্তব্য"
    ,"Reports are saved only in this local preview. No moderation team receives them yet." to "রিপোর্ট শুধু এই স্থানীয় নমুনায় থাকে। এখনো কোনো মডারেশন দল পায় না।"
    ,"This hides this post in the current preview screen. The block is not synced to an account." to "এতে এই নমুনা পর্দায় পোস্টটি লুকানো হবে। ব্লকটি অ্যাকাউন্টে সিঙ্ক হবে না।"
    ,"Loading photo…" to "ছবি লোড হচ্ছে…", "System default" to "সিস্টেমের ডিফল্ট কণ্ঠ"
    ,"Listen with Premium" to "প্রিমিয়ামে শুনুন", "Community guidelines" to "কমিউনিটির নির্দেশিকা", "Create post" to "পোস্ট তৈরি করুন"
    ,"Create discussion" to "আলোচনা শুরু করুন", "Send reply" to "উত্তর পাঠান"
)

private fun banglaDigits(value: String): String = value.map { character -> if (character in '0'..'9') '০' + (character - '0') else character }.joinToString("")

@Composable
internal fun localizedAppCopy(text: String): String {
    val isBangla = LocalConfiguration.current.locales[0].language == "bn"
    if (!isBangla) return text
    return when {
        text.startsWith("by ") -> "লেখক: ${text.removePrefix("by ")}"
        text.startsWith("Search results (") && text.endsWith(")") -> "অনুসন্ধানের ফলাফল (${banglaDigits(text.substringAfter('(').dropLast(1))})"
        text.startsWith("Ages ") && text.contains("   ·   ") -> {
            val (age, pages) = text.split("   ·   ", limit = 2)
            "বয়স ${banglaDigits(age.removePrefix("Ages "))} · ${banglaDigits(pages.substringBefore(' '))} পৃষ্ঠা"
        }
        text.startsWith("⭐ ") && text.contains("reviews") -> "⭐ ${banglaDigits(text.substringAfter("⭐ ").substringBefore("  ·"))} · ${banglaDigits(text.substringAfter("· ").substringBefore(' '))}টি রিভিউ"
        text.startsWith("Mode: ") -> "পাঠের ধরন: ${when (text.removePrefix("Mode: ")) { "Page turn" -> "পৃষ্ঠা ওল্টানো"; "Scroll" -> "স্ক্রল"; else -> "PDF" }}"
        text.startsWith("Voice: ") && text.contains(" · Speed: ") -> {
            val voice = text.substringAfter("Voice: ").substringBefore(" · Speed:")
            val speed = text.substringAfter(" · Speed: ")
            "কণ্ঠ: ${banglaCopy[voice] ?: voice} · গতি: ${banglaDigits(speed)}"
        }
        text.startsWith("Block ") && text.endsWith("?") -> "${text.removePrefix("Block ").dropLast(1)}-কে ব্লক করবেন?"
        text.contains(" will be removed from the sample list.") -> {
            val title = text.substringBefore(" will be removed from the sample list.")
            "${banglaCopy[title] ?: title} নমুনা তালিকা থেকে সরানো হবে। ডিভাইসে সংরক্ষিত কোনো বইয়ের ফাইল মুছবে না।"
        }
        text.startsWith("Reference ") -> "রেফারেন্স ${text.removePrefix("Reference ")}"
        text.startsWith("Delivery to ") -> "ডেলিভারির ঠিকানা: ${text.removePrefix("Delivery to ")}"
        text.endsWith(" · English and বাংলা") -> "${localizedAge(text.substringBefore(" · English and বাংলা"))} · ইংরেজি ও বাংলা"
        text.contains(" stories started · Brave Little Fox page ") -> {
            val count = text.substringBefore(" stories started")
            val page = text.substringAfter("Brave Little Fox page ").substringBefore(" of")
            "${banglaDigits(count)}টি গল্প শুরু হয়েছে · সাহসী ছোট্ট শিয়াল, পৃষ্ঠা ${banglaDigits(page)} / ২৪"
        }
        text.matches(Regex("\\d+ / 24")) -> "${banglaDigits(text.substringBefore(" /"))} / ২৪"
        text.startsWith("Selected: ") -> "নির্বাচিত: ${text.substringAfter("Selected: ").substringBefore(". Payment")}। পেমেন্ট এখনো চালু হয়নি। PDF শুধু অ্যাপেই পড়া যাবে।"
        text.startsWith("The Brave Little Fox · ") && text.contains(" of 24 pages") -> "সাহসী ছোট্ট শিয়াল · ২৪টির মধ্যে ${banglaDigits(text.substringAfter("· ").substringBefore(" of"))} পৃষ্ঠা"
        text.endsWith("% complete") -> "${banglaDigits(text.substringBefore('%'))}% সম্পন্ন"
        text.startsWith("Page ") -> "পৃষ্ঠা ${banglaDigits(text.substringAfter("Page "))}"
        text.startsWith("Cover for ") -> "${text.removePrefix("Cover for ")} বইয়ের প্রচ্ছদ"
        text.startsWith("Sample PDF page ") -> "নমুনা PDF-এর পৃষ্ঠা ${banglaDigits(text.removePrefix("Sample PDF page "))}"
        text.startsWith("Rate ") && text.endsWith(" out of 5 stars") -> "৫টির মধ্যে ${banglaDigits(text.removePrefix("Rate ").substringBefore(' '))} তারকা রেটিং"
        text.startsWith("Remove ") && text.endsWith(" from preview list") -> "${text.removePrefix("Remove ").removeSuffix(" from preview list")} নমুনা তালিকা থেকে সরান"
        text.startsWith("Edit ") && text.endsWith(" profile") -> "${text.removePrefix("Edit ").removeSuffix(" profile")} প্রোফাইল সম্পাদনা করুন"
        else -> banglaCopy[text] ?: text
    }
}

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
    val renderedText = localizedAppCopy(text)
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

private fun localizedAge(value: String): String = when {
    value.startsWith("Ages ") -> "বয়স ${banglaDigits(value.removePrefix("Ages "))}"
    value.matches(Regex("\\d+ years old")) -> "${banglaDigits(value.substringBefore(' '))} বছর বয়স"
    else -> banglaCopy[value] ?: banglaDigits(value)
}
