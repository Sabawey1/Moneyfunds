# FundTracker

A personal fund tracker that reads your bank SMS messages **on your phone only** and builds your transaction history. Unlike rigid trackers, it shows you any money-related messages it couldn't classify (the "Missed" tab) and lets you add your own keywords (the "Rules" tab) so nothing slips through.

Everything stays on your device. No internet permission, no servers, no data leaves your phone.

## How to get the APK (no coding tools needed)

You'll use GitHub's free cloud build service. Takes about 10 minutes the first time.

### Step 1 — Create a GitHub account
Go to https://github.com and sign up (free).

### Step 2 — Create a repository
1. Click the **+** in the top right → **New repository**
2. Name it `fundtracker`, keep it **Private**, click **Create repository**

### Step 3 — Upload the project files
1. On the new repo page, click **uploading an existing file**
2. Open the FundTracker folder you downloaded on your computer
3. Select ALL files and folders inside it and drag them into the GitHub upload box
   (make sure hidden files are visible: Windows → View → Hidden items; Mac → press Cmd+Shift+.)
4. Click **Commit changes**

**If the `.github` folder didn't upload** (it's hidden, so this happens often):
1. In your repo, click **Add file → Create new file**
2. In the filename box type exactly: `.github/workflows/build.yml`
3. Paste in the contents of the `build.yml` file from this project
4. Click **Commit changes**

### Step 4 — Let GitHub build the APK
1. Click the **Actions** tab at the top of your repo
2. If asked, click **"I understand my workflows, enable them"**
3. A build called **Build APK** starts automatically (or click it → **Run workflow**)
4. Wait ~5 minutes for the green check mark

### Step 5 — Download and install
1. Click the finished build → scroll down to **Artifacts** → download **FundTracker-APK**
2. Unzip it — inside is `app-debug.apk`
3. Send it to your phone (email it to yourself, Google Drive, USB cable, etc.)
4. On your phone, tap the file. Android will ask you to allow installs from unknown sources — allow it for that app (e.g. Files or Gmail)
5. Open FundTracker and grant SMS permission

## Using the app

- **Overview** — money in/out, balances per account (read from "Avl Bal" texts), spending by category
- **Txns** — every parsed transaction, filterable by debit/credit
- **Missed** — money-related messages the parser couldn't classify. This is the fix for trackers that silently skip messages: you can SEE what was missed
- **Rules** — add the exact word your bank uses (e.g. "dr to") as a debit or credit keyword. The app re-scans instantly and catches all similar messages

## Notes

- The APK is a debug build for personal use; it can't go on the Play Store (Google restricts SMS-reading apps anyway).
- The app re-reads your SMS inbox every time it opens, so it's always up to date and never loses data.

## Managing accounts (v4)

- On **Home**, tap **Manage** next to "Your accounts" to add, rename, set balances, link, or remove accounts.
- **Add account** — create a Cash or Wallet account you control by hand (great for cash you carry).
- **Link** — if CIB shows your account (e.g. ••4215) and its debit card (e.g. ••5534) as two separate things with the same balance, tap **Link** on one and pick the other. They merge into one account and stop double-counting your total.
- **Balance** — set or correct any account's balance. **Use bank balance** reverts to the figure from your latest bank SMS.
- The **Add** button (Home/Activity) logs cash spending or money you received, and updates that account's balance.
