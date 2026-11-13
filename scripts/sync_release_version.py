#!/usr/bin/env python3
import re
import os

def get_gradle_version():
    gradle_path = "app/build.gradle.kts"
    with open(gradle_path, "r", encoding="utf-8") as f:
        content = f.read()
    match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    if match:
        return match.group(1)
    raise ValueError("versionName not found in build.gradle.kts")

def get_latest_changelog():
    changelog_path = "CHANGELOG.md"
    with open(changelog_path, "r", encoding="utf-8") as f:
        content = f.read()

    match = re.search(r'##\s+\[([\d\.]+)\](.*?)(?=\n##\s+\[|\Z)', content, re.DOTALL)
    if match:
        version = match.group(1)
        section_text = match.group(2)
        # Extract main top-level bullet points (lines starting with "- **")
        matches = re.findall(r'(-\s+\*\*.*?(?=\n\s*-|\n[^\s-]|\Z))', section_text, re.DOTALL)
        bullets = [m.strip()[2:] for m in matches] if matches else [line.strip()[2:] for line in section_text.splitlines() if line.strip().startswith("- ")]
        return version, bullets
    raise ValueError("Latest changelog section not found")

def update_readme(version, bullets):
    readme_path = "README.md"
    with open(readme_path, "r", encoding="utf-8") as f:
        content = f.read()

    content = re.sub(
        r'\[\!\[Version\].*?\]\(https://github\.com/[^)]+/releases\)',
        f'[![Version](https://img.shields.io/badge/Version-{version}-blue.svg)](https://github.com/sreejith19931/SplitUp/releases)',
        content
    )

    bullet_md = "\n".join([f"- {b}" for b in bullets])
    new_whats_new = f"## ⚡ What's New in v{version}\n\n{bullet_md}"

    content = re.sub(
        r'##\s+⚡\s+What\'s\s+New\s+in\s+v[\d\.]+.*?(?=\n---|\n##|\Z)',
        new_whats_new,
        content,
        flags=re.DOTALL
    )

    with open(readme_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Updated README.md for version {version}")

def update_landing_page(version, bullets):
    landing_path = "landing/index.html"
    if not os.path.exists(landing_path):
        return
    with open(landing_path, "r", encoding="utf-8") as f:
        content = f.read()

    content = re.sub(
        r'<div class="version-badge">\s*SplitUp v[\d\.]+ is out\s*</div>',
        f'<div class="version-badge">\n            SplitUp v{version} is out\n        </div>',
        content
    )

    content = re.sub(
        r'Download Android APK \(v[\d\.]+\)',
        f'Download Android APK (v{version})',
        content
    )

    content = re.sub(
        r'<div class="release-title">What\'s New in v[\d\.]+</div>',
        f'<div class="release-title">What\'s New in v{version}</div>',
        content
    )

    li_html = ""
    for b in bullets:
        parts = b.split(":", 1)
        if len(parts) == 2:
            title, desc = parts[0].strip(), parts[1].strip()
            title = re.sub(r'^\*\*(.*)\*\*$', r'\1', title)
            li_html += f'                <li>\n                    <strong>{title}:</strong> {desc}\n                </li>\n'
        else:
            li_html += f'                <li>\n                    {b}\n                </li>\n'

    content = re.sub(
        r'<ul class="release-list">.*?</ul>',
        f'<ul class="release-list">\n{li_html}            </ul>',
        content,
        flags=re.DOTALL
    )

    with open(landing_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Updated landing/index.html for version {version}")

if __name__ == "__main__":
    ver = get_gradle_version()
    _, bullets = get_latest_changelog()
    update_readme(ver, bullets)
    update_landing_page(ver, bullets)
    print("Version synchronization completed successfully.")
