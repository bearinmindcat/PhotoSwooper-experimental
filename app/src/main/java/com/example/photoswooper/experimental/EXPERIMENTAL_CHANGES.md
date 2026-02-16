# Experimental Module - Changes to Existing Files

This file tracks all modifications made to existing (non-experimental) files to support the experimental document swiping feature. When the upstream PhotoSwooper app updates, re-apply these changes.

## Modified Existing Files

### 1. `data/database/MediaStatusDatabase.kt`
- Added `DocumentEntity` to `@Database(entities = [...])`
- Added `abstract fun documentStatusDao(): DocumentStatusDao`
- Added `MIGRATION_4_5` for the `documentStatus` table
- Bumped database version from 4 to 5

### 2. `ui/view/TabbedSheetContent.kt`
- Added `EXPERIMENTAL` to `TabIndex` enum
- Added experimental tab icon + content
- Added `documentSwipeViewModel` parameter
- Added `imageLoader: ImageLoader` parameter (passed to DocumentReviewScreen)
- Adjusted tab offset logic for the new tab
- REVIEW tab now shows `DocumentReviewScreen` when `docUiState.isSwipeMode` is true
- Added imports: `DocumentReviewScreen`, `ImageLoader`

### 3. `MainActivity.kt`
- Created `DocumentSwipeViewModel` instance
- Passed it to `MainScreen` composable
- Added `SvgDecoder.Factory()` and `GifDecoder.Factory()` to ImageLoader components

### 9. `AndroidManifest.xml`
- Added `<provider>` for FileProvider (required for opening `file://` documents in external apps)
- Added `res/xml/file_paths.xml` with `external-path` and `cache-path`

### 4. `data/Preferences.kt`
- Added `DOCUMENT_SCAN_FOLDERS` to `StringPreference` enum
- Added `DOCUMENT_EXCLUDED_EXTENSIONS` to `StringPreference` enum

### 5. `ui/viewmodels/MainViewModel.kt`
- Added `: SwipeController` interface implementation
- Added `override` keyword to: `animatedMediaScale`, `onMediaLoaded`, `onMediaError`, `next`
- Split `markItem` into 1-param override (interface) + 2-param version (for ReviewScreen)
- Split `toggleInfoAndFloatingActionsRow` into 0-param override (interface) + 1-param version (for TutorialController)
- Added `override fun getCurrentItemSize(): Long?`
- Added import: `com.example.photoswooper.experimental.SwipeController`

### 6. `ui/components/SwipeableMediaWithIndicatorIcons.kt`
- Changed signature from `(media: Media, viewModel: MainViewModel, imageLoader: ImageLoader)` to `(item: SwipeableItem, controller: SwipeController, imageLoader: ImageLoader?, isReady: Boolean, mediaAspectRatio: Float, modifier)`
- Replaced all `viewModel.` calls with `controller.`
- Replaced `Media`/`MainViewModel` references with `SwipeableItem`/`SwipeController`
- Added document rendering branch (`DocumentPreviewCard`) alongside photo/video, passing `imageLoader`
- Removed internal `uiState` collection (now uses `isReady` param)
- Added imports: `SwipeController`, `SwipeableItem`, `DocumentPreviewCard`
- Removed imports: `MainViewModel`, `Media`

### 7. `ui/view/MainScreen.kt`
- Replaced `DocumentSwipeScreen` call with `SwipeableMediaWithIndicatorIcons` using `SwipeableItem.DocumentItem`
- Updated photo/video `SwipeableMediaWithIndicatorIcons` call to new signature
- Added document floating actions row (`DocumentFloatingActionsRow`) + `DocumentInfoRow` AnimatedVisibility
- Added `BackHandler` for document swipe mode
- `numToDelete` now switches to document count when in document swipe mode
- ActionBar undo/delete actions are now mode-aware (documents vs photos)
- Changed `imageLoader = null` to `imageLoader = imageLoader` for document swipe
- Passed `imageLoader = imageLoader` to `TabbedSheetContent()`
- Added imports: `SwipeableItem`, `DocumentFloatingActionsRow`, `DocumentInfoRow`, `size`
- Removed import: `DocumentSwipeScreen`

### 8. `ui/components/ActionBar.kt`
- Added `deleteMedia` and `onUndo` lambda parameters (with defaults to existing behavior)
- Undo button now uses `onUndo()` lambda
- ReviewDeletedButton now uses `deleteMedia()` lambda

## New Experimental Files
- `experimental/SwipeController.kt` - Interface for swipe composable ViewModel abstraction
- `experimental/data/SwipeableItem.kt` - Sealed class wrapping Media or Document
- `experimental/ui/DocumentFloatingActionsRow.kt` - Floating action buttons for document swipe mode
- `experimental/ui/DocumentPreviewCard.kt` - Document preview rendering (PDF, Image, APK, EPUB, Office, Text, generic)
- `experimental/ui/DocumentInfoRow.kt` - Document info details row
- `experimental/ui/DocumentReviewScreen.kt` - Document review screen with thumbnails (image, PDF, APK, EPUB covers), FileProvider-based "Open with" intent on tap
- `experimental/ui/ExperimentalScreen.kt` - Experimental tab content screen
- `experimental/data/` - Document model, DocumentType, DocumentFilter, database entity+DAO
- `experimental/utils/DocumentResolverInterface.kt` - SAF-based document scanning
- `experimental/viewmodel/DocumentSwipeViewModel.kt` - Document swiping ViewModel

## Deleted Files
- `experimental/ui/DocumentSwipeCard.kt` - Replaced by shared SwipeableMediaWithIndicatorIcons
- `experimental/ui/DocumentSwipeScreen.kt` - Replaced by MainScreen document routing
