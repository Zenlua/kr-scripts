#!/system/bin/sh

if [ -d "$TOOLKIT" ]; then
    cd "$TOOLKIT"
    if [[ ! -f busybox_installed ]]; then
        busybox --install $TOOLKIT
        echo > busybox_installed
    fi
fi
