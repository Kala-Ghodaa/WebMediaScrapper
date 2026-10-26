# Telegram Album Folder Media Sharing, Permanent Album Links & Downloads Search/Collapse

Enable one-tap sharing of downloaded album folders to Telegram as grouped, inline playable media with searchable `#hashtag` captions, index folders into Android's Gallery buckets (`DCIM/PulseGrab/<Album>`), add **Search + Collapse All / Expand All** to the Downloads screen, and **fix "Verify on Disk"** so it never deletes your album history—preserving every folder's source album link for instant re-downloading at any time.

## User Review & Critical Decisions

> [!IMPORTANT]
> **Feasibility Confirmation & Bug Fix for "Verify on Disk"**:
> 1. **100% Native Android SDK Feasibility**: Everything in this plan uses official Android OS APIs (`MediaStore.Images.Media`, `MediaStore.Video.Media`, `MediaScannerConnection`, and `Intent.ACTION_SEND_MULTIPLE` with `ClipData`). No unstable external plugins are required.
> 2. **Why "Verify on Disk" Removed Your Album History & How We Fix It**:
>    - *Root Cause*: Previously, `verifyAndRedownloadMissingAlbums()` called `downloadDao.deleteDownloadedAlbum()` whenever `ParcelFileDescriptor.statSize` returned `-1` (which Android FUSE storage often returns for valid MediaStore URIs) or when files were missing—wiping the album record from the database instead of re-downloading it.
>    - *The Fix*:
>      1. **Never Delete Album History on Verify**: `downloaded_albums` will act as a **Permanent Album & Link Registry**. Verifying on disk will **never** delete your album records. Instead, if files are missing, it will **automatically re-scrape the saved `albumUrl` link and re-download the missing files** into the folder!
>      2. **Auto-Recover Deleted History on Startup**: We will reconstruct any lost `downloaded_albums` entries from your existing `download_tasks` (`refererUrl` + `folderName`) and on-disk folders automatically upon launch.
>      3. **Accurate Stream-Based File Verification**: Replace the faulty `statSize > 0` check with `ContentResolver.openInputStream` + `MediaStore` column queries across both `Download/PulseGrab/` and `DCIM/PulseGrab/`.

- **Confirmed Decision 1 — Permanent Album History & Source Link on Every Folder**: Every download folder in the Downloads screen (and in the Album History list) will permanently store and display its **Source Album URL (`albumUrl`)** with 1-tap buttons to **Re-Download Missing Files**, **Copy Album Link**, or **Open Album in Browser**.
- **Confirmed Decision 2 — Downloads Search Bar & Collapse All / Expand All**: Add a real-time **Search Bar** (by folder name, file name, or album link) and a **Collapse All / Expand All** toggle button in the Downloads screen header.
- **Confirmed Decision 3 — One-Tap Telegram Inline Media Share & Gallery Indexing**: Share folders via `ACTION_SEND_MULTIPLE` using `image/*` and `video/*` gallery URIs with auto-generated `#Hashtag + Title` captions, and index folders under `DCIM/PulseGrab/<Album>` so they appear as selectable albums inside Telegram's native Gallery picker.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Permanent Album Link Registry & Smart Verify/Re-Download**:
     - Every downloaded folder permanently retains its source page/album URL (`albumUrl`).
     - Tapping **"Verify on Disk"** checks every folder and file accurately: if the files exist on disk, it marks them verified and skips; if any folder or files are missing, it **keeps the album record** and **automatically re-downloads the missing media from the saved `albumUrl`**.
  2. **Downloads Search & Collapse All / Expand All**:
     - Quickly filter across hundreds of downloaded folders and files by typing any part of the album title, folder name, filename, or source URL.
     - Collapse all album folders into compact summary cards with one tap (`Collapse All`) or expand them all (`Expand All`).
  3. **One-Tap "Share Album to Telegram" (Inline Media + Searchable `#Hashtag`)**:
     - Packages all completed photos and videos in an album folder as inline playable media (`image/*`, `video/*`) with an editable `#Album_Hashtag • Album Title` caption (also auto-copied to clipboard) and optional 10-item Telegram Album Group chunking.
  4. **Telegram Gallery Album Bucket Indexing (`DCIM/PulseGrab/<Album>`)**:
     - Saves and indexes photos (`MediaStore.Images.Media`) and videos (`MediaStore.Video.Media`) into `DCIM/PulseGrab/<Album_Title>` and triggers `MediaScannerConnection.scanFile`, making each folder appear as a named album in Telegram's **Attach → Gallery** dropdown.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Downloads Screen — Search, Collapse All & Album Link Banner**:
     - **Search Field**: Located in the Downloads header; filters folders and files live as you type, with a quick clear (`X`) button.
     - **Collapse All / Expand All Pill**: Located in the bulk action strip; toggles all folders between collapsed summary mode and expanded file list mode.
     - **Enhanced Album Folder Banner (`AlbumFolderMergedGlassBanner`)**:
       - Displays the folder name, completion progress, and the **clickable/copyable Source Album URL** (`🔗 https://.../album-id`).
       - Action pills:
         - **`Share to Telegram (N)`**: Opens the Telegram inline media share sheet with `#Hashtag` caption.
         - **`Verify & Re-Download Missing`**: Re-fetches the saved `albumUrl` and downloads any missing files for that folder.
         - **`Copy Link` / `Open Source`**: Copies or opens the original album webpage.
  2. **Saved Albums & Links Registry (Even When Tasks Are Cleared)**:
     - If you clear completed tasks from the active list or want to re-download an older album whose folder was deleted from your phone, the **Saved Album History** section lists every album with its **Source Link** and a 1-tap **"Re-Download Album"** button.
  3. **Telegram Folder Share Sheet**:
     - Shows the auto-generated `#Hashtag` and Album Title caption (with an option to include the source link if desired).
     - Offers **"Share Entire Folder as Media"** and **"Send in 10-Item Telegram Album Groups (Part 1, Part 2…)"**.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Permanent Album History vs. Deleting Records on Verify**
  - *Chosen Approach*: Never delete `DownloadedAlbumEntity` rows during disk verification. Instead, use `DownloadedAlbumEntity` as the source of truth to **re-download** missing folders from `album.albumUrl`. Only delete a history record if the user explicitly taps the trash icon on that specific history item.
  - *Why*: If a user deletes files from their phone's storage (or moves them to a PC) and taps "Verify on Disk", they need the app to know the `albumUrl` so it can re-download the album—deleting the record destroyed the link needed to recover the files.
- **Decision 2: Dual `DCIM/PulseGrab/<Album>` & `Download/PulseGrab/<Album>` Recognition**
  - *Chosen Approach*: Index images and videos into `DCIM/PulseGrab/<Album>` via `MediaStore.Images` and `MediaStore.Video` while checking both `DCIM/PulseGrab/<Album>` and `Download/PulseGrab/<Album>` during disk verification.
  - *Why*: Ensures backward compatibility with all previously downloaded folders while guaranteeing that Telegram recognizes shared URIs as inline playable gallery media rather than document files.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          DownloadsScreen & Sheets                            │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ Header Controls:                                                       │  │
│  │ • Search Bar (filters by Folder Name, File Name, or Source Album URL)  │  │
│  │ • "Collapse All / Expand All" Toggle Pill                              │  │
│  │ • "Verify & Re-Download Missing" Action                                │  │
│  └───────────────────────────────────┬────────────────────────────────────┘  │
│                                      ▼                                       │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │ AlbumFolderMergedGlassBanner (Per Folder)                              │  │
│  │ • Folder Title + Progress + Source Album Link (🔗 albumUrl)            │  │
│  │ • [Share to Telegram]  [Re-Download Missing]  [Open Link]  [Logs]      │  │
│  └─────────────────┬──────────────────────────────────┬───────────────────┘  │
└────────────────────┼──────────────────────────────────┼──────────────────────┘
                     │                                  │
                     ▼                                  ▼
┌────────────────────────────────────────┐ ┌───────────────────────────────────┐
│      TelegramFolderShareSheet          │ │  Permanent Album & Link Registry  │
│ • Auto #Hashtag + Album Title Caption  │ │ • Auto-recovers album links from  │
│ • Share All or 10-Item Media Groups    │ │   download_tasks refererUrl       │
│ • Resolves MediaStore Images/Video URI │ │ • Re-scrapes albumUrl when files  │
│ • Auto-copies caption to Clipboard     │ │   are missing on disk             │
└────────────────────────────────────────┘ └───────────────────────────────────┘
```

### Interactive Component & State Mapping
1. **Accurate File & Folder Existence Check (`DownloadRepository`)**:
   - `doesTaskFileExist`: Checks `ContentResolver.openInputStream(uri)` readability and queries `MediaStore` (`Images`, `Video`, and `Downloads` collections) + physical file paths in both `DCIM/PulseGrab/` and `Download/PulseGrab/` using both raw and sanitized folder names.
   - `verifyAndRedownloadMissingAlbums` & `verifyAndRedownloadMissingTasks`:
     - First runs `syncAlbumRegistryFromTasks()` so every folder with a `refererUrl` is guaranteed to be saved in `downloaded_albums`.
     - For any album whose folder or files are missing on disk, fetches `album.albumUrl` via `fetchFreshAlbumItems(album.albumUrl)` and enqueues the missing media items into `album.folderName`—never deleting the album history record.
2. **Collapse All / Expand All & Downloads Search (`MainDownloaderViewModel`)**:
   - `downloadSearchQuery: String` state in `InternalControlState` and `MainUiState` filters `filteredDownloadTasks` by matching `folderName`, `fileName`, or `refererUrl` (and matching album record URL/title).
   - `toggleCollapseAllFolders()` checks if all currently visible folders are in `collapsedFolderNames`: if so, clears `collapsedFolderNames` (Expands All); otherwise, adds all visible folder names to `collapsedFolderNames` (Collapses All).
3. **One-Tap Re-Download Album from History or Folder Banner (`redownloadAlbumFromUrl`)**:
   - Allows re-downloading any album directly from its saved `albumUrl` in either the `AlbumFolderMergedGlassBanner` or the `AlbumBatchGrabberSheet` history list, even if 100% of its local files were deleted.
4. **Telegram Media Share & Gallery Indexing (`TelegramMediaShareHelper` + `DownloadRepository`)**:
   - Saves/promotes images to `MediaStore.Images.Media` and videos to `MediaStore.Video.Media` under `DCIM/PulseGrab/<Album_Folder>` and invokes `MediaScannerConnection.scanFile`.
   - Launches `Intent.ACTION_SEND_MULTIPLE` with `ClipData`, `EXTRA_MIME_TYPES = arrayOf("image/*", "video/*")`, and `EXTRA_TEXT` (`#Hashtag\n📁 Album Title`).
