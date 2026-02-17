# Potential Things To Work On

- Most video files are encoded correctly unless it's MKV/Hi10P or condensed files, to be able to add this I would need to add a FFmpeg decoder.
- Currently previews document and similar files (like xls & ppt) they are rendered through plain text like a .txt document with the Apache POI library but don't have full format rendering. In order to enable this I would need to parse the XML to an HTML and render in something like Android Webview but that creates a lot of lag. I could also convert to PDF or with Compose but either options would add extra time for rendering and you would need to wait some time before it renders fully in the swipe area itself.
