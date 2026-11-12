# Guide: Publishing SplitUp to GitHub & Vercel

Since **SplitUp** is a native Android application (built with Kotlin and Jetpack Compose), the Android app itself runs on devices rather than web servers. Therefore, **Vercel** cannot run the Android APK directly.

However, the standard industry practice is to:
1. **Publish the source code repository on GitHub** (with a professional README).
2. **Deploy a Project Landing Page / Download Web App on Vercel** (which gets you a `*.vercel.app` domain, showcasing features, screenshots, and providing APK/GitHub download links).

---

## Part 1: Preparing & Publishing SplitUp on GitHub

### Step 1: Initialize Git and Configure Ignore Rules
Open your terminal in the project root (`C:/Users/Sreejith/AndroidStudioProjects/SplitUp`) and run:

```bash
git init
```

Ensure your `.gitignore` file correctly excludes build files, local properties, and sensitive configuration. Update `.gitignore` to include:
```gitignore
/build/
.gradle/
/app/build/
/local.properties
.idea/workspace.xml
.idea/caches/
.idea/libraries/
/app/release/
google-services.json
```

### Step 2: Add Firebase Configuration Template
Since `google-services.json` contains your Firebase project identifiers, exclude it from version control and create a template `app/google-services.json.template`:
```json
{
  "project_info": {
    "project_number": "YOUR_PROJECT_NUMBER",
    "project_id": "YOUR_PROJECT_ID",
    "storage_bucket": "YOUR_STORAGE_BUCKET"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "YOUR_MOBILE_SDK_APP_ID",
        "android_client_info": {
          "package_name": "com.finance.splitup"
        }
      },
      "api_key": [
        {
          "current_key": "YOUR_FIREBASE_API_KEY"
        }
      ]
    }
  ],
  "configuration_version": "1"
}
```

### Step 3: Add a License (`LICENSE`)
Create a `LICENSE` file in the root directory (e.g., **MIT License**):
```text
MIT License

Copyright (c) 2025 SplitUp

Permission is hereby granted, free of charge, to any person obtaining a copy...
```

### Step 4: Create a Professional `README.md`
Create a `README.md` in the root folder describing features, tech stack, and setup instructions.

### Step 5: Commit and Push to GitHub
1. Create a new public repository on [GitHub](https://github.com/new) named `SplitUp`.
2. Run these commands in your project terminal:
```bash
git add .
git commit -m "Initial commit: SplitUp Android Finance & Expense Splitter app"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/SplitUp.git
git push -u origin main
```

---

## Part 2: Publishing a Landing Page on Vercel (`*.vercel.app`)

To give SplitUp a web presence with a `*.vercel.app` domain:

### Step 1: Create a Simple Landing Page Folder
Create a `landing/` folder in your project root with an `index.html`:
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SplitUp - Expense Sharing & Finance Tracker</title>
    <style>
        body { font-family: system-ui, -apple-system, sans-serif; margin: 0; padding: 0; background: #0f172a; color: #f8fafc; display: flex; justify-content: center; align-items: center; height: 100vh; text-align: center; }
        .container { max-width: 600px; padding: 2rem; }
        h1 { font-size: 3rem; margin-bottom: 0.5rem; background: linear-gradient(to right, #38bdf8, #818cf8); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
        p { color: #94a3b8; font-size: 1.2rem; margin-bottom: 2rem; }
        .btn { display: inline-block; background: #38bdf8; color: #0f172a; padding: 0.75rem 1.5rem; border-radius: 9999px; font-weight: bold; text-decoration: none; margin: 0.5rem; transition: background 0.2s; }
        .btn:hover { background: #0ea5e9; }
        .btn-outline { background: transparent; border: 2px solid #38bdf8; color: #38bdf8; }
        .btn-outline:hover { background: #38bdf8; color: #0f172a; }
    </style>
</head>
<body>
    <div class="container">
        <h1>SplitUp</h1>
        <p>The ultimate open-source expense splitting and personal finance companion for Android, built with Jetpack Compose.</p>
        <div>
            <a href="https://github.com/YOUR_USERNAME/SplitUp" class="btn">View on GitHub</a>
            <a href="https://github.com/YOUR_USERNAME/SplitUp/releases" class="btn btn-outline">Download APK</a>
        </div>
    </div>
</body>
</html>
```

### Step 2: Deploy to Vercel
1. Install Vercel CLI (or use the Vercel Web Dashboard):
   ```bash
   npm i -g vercel
   ```
2. Navigate to your landing directory or project root and run:
   ```bash
   vercel
   ```
3. Follow the CLI prompts:
   - Set up and deploy? **Y**
   - Which scope? **Your Account**
   - Link to existing project? **N**
   - What's your project name? **splitup**
   - In which directory is your code located? **landing** (or `./`)
4. Vercel will deploy your site and provide a live production URL:
   `https://splitup-xyz.vercel.app` (or similar custom `*.vercel.app` domain).
