#!/usr/bin/env python3
"""Sweep remaining Material icons in the chat-critical files to GsIcons."""
import re

MAP = {
    "Icons.Outlined.Close": "GsIcons.X",
    "Icons.Outlined.ErrorOutline": "GsIcons.CircleAlert",
    "Icons.Outlined.Image": "GsIcons.Image",
    "Icons.Outlined.InsertDriveFile": "GsIcons.File",
    "Icons.Outlined.PictureAsPdf": "GsIcons.FileText",
    "Icons.Outlined.Refresh": "GsIcons.RefreshCw",
    "Icons.Outlined.ChatBubbleOutline": "GsIcons.MessageCircle",
    "Icons.Outlined.ManageSearch": "GsIcons.TextSearch",
    "Icons.Outlined.SearchOff": "GsIcons.SearchX",
    "Icons.Filled.Star": "GsIcons.Star",
    "Icons.Outlined.Add": "GsIcons.Plus",
    "Icons.Outlined.Archive": "GsIcons.Archive",
    "Icons.Outlined.Inbox": "GsIcons.Inbox",
    "Icons.Outlined.MoreVert": "GsIcons.EllipsisVertical",
    "Icons.Outlined.Search": "GsIcons.Search",
    "Icons.Outlined.BrokenImage": "GsIcons.ImageOff",
    "Icons.Outlined.Cancel": "GsIcons.X",
    "Icons.Outlined.CheckCircle": "GsIcons.CircleCheck",
    "Icons.Outlined.ExpandMore": "GsIcons.ChevronDown",
    "Icons.Outlined.Delete": "GsIcons.Trash2",
    "Icons.Outlined.DriveFileRenameOutline": "GsIcons.Pencil",
    "Icons.Outlined.PushPin": "GsIcons.Pin",
    "Icons.Outlined.CloudOff": "GsIcons.WifiOff",
}

FILES = [
    "android/app/src/main/java/com/grapsee/gsai/ui/attachment/AttachmentChipRow.kt",
    "android/app/src/main/java/com/grapsee/gsai/ui/chat/ChatSearchScreen.kt",
    "android/app/src/main/java/com/grapsee/gsai/ui/chat/ChatsScreen.kt",
    "android/app/src/main/java/com/grapsee/gsai/ui/chat/content/RichBlocks.kt",
    "android/app/src/main/java/com/grapsee/gsai/ui/components/ConversationActions.kt",
    "android/app/src/main/java/com/grapsee/gsai/ui/components/GsComponents.kt",
]

IMPORT_LINE = "import com.grapsee.gsai.ui.icons.GsIcons"

for f in FILES:
    s = open(f).read()
    orig = s
    used = set()
    for a, b in MAP.items():
        if a in s:
            used.add(b)
            s = s.replace(a, b)
    if s != orig:
        # add the GsIcons import after the last existing import line
        if IMPORT_LINE not in s:
            imports = [m.start() for m in re.finditer(r"^import .*$", s, flags=re.M)]
            last = s.find("\n", imports[-1])
            s = s[:last + 1] + IMPORT_LINE + "\n" + s[last + 1:]
        open(f, "w").write(s)
        print(f"{f}: swapped, uses {sorted(used)}")
    else:
        print(f"{f}: no changes")
