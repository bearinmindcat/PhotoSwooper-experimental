# PhotoSwooperModded

Added experimental document swiping functionality to the original PhotoSwooper app. Swipe through documents, audio, and video files the same way you swipe photos. All original credits go to the owner here https://codeberg.org/Loowiz/PhotoSwooper and you can see all the changes I made by looking at the experimental folder and EXPERIMENTAL_CHANGES.md file.

**Link to releases:** [https://gitlab.com/bearincrypto1/photoswoopermodded/-/releases](https://gitlab.com/bearincrypto1/photoswoopermodded/-/releases)

## Get from source

```bash
git clone https://gitlab.com/bearincrypto1/photoswoopermodded.git
```

## Modded Code

```
com.example.photoswooper/
└── experimental/                              # All mod-specific code
    ├── data/
    │   ├── database/
    │   │   ├── DocumentEntity.kt              # Room entity for document status tracking
    │   │   └── DocumentStatusDao.kt           # DAO for document database operations
    │   ├── Document.kt                        # Document data model
    │   ├── DocumentFilter.kt                  # Filter/sort options for documents
    │   ├── DocumentType.kt                    # Document type enum (PDF, text, audio, video, etc.)
    │   └── SwipeableItem.kt                   # Shared swipeable item interface
    ├── ui/
    │   ├── DocumentFloatingActionsRow.kt      # Floating action buttons (snooze, info, share, open)
    │   ├── DocumentInfoRow.kt                 # Document metadata info overlay
    │   ├── DocumentPreviewCard.kt             # Preview card with PDF/text/audio/video/image support
    │   ├── DocumentReviewScreen.kt            # Review swiped documents before deletion
    │   └── ExperimentalScreen.kt              # Main experimental tab with folder picker and settings
    ├── utils/
    │   └── DocumentResolverInterface.kt       # File scanning and MediaStore trash integration
    ├── viewmodel/
    │   └── DocumentSwipeViewModel.kt          # ViewModel for document swipe state management
    ├── SwipeController.kt                     # Shared swipe controller interface
    └── EXPERIMENTAL_CHANGES.md                # Complete log of changes to the original codebase
```

## License

This project is licensed under the AGPL-3.0 License - see the [LICENSE](LICENSE) file for details.

## Credits

Based on [PhotoSwooper](https://codeberg.org/Loowiz/PhotoSwooper) by Loowiz.
