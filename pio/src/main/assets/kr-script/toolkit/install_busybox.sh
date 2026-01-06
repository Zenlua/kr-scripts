#!/system/bin/sh
if [ ! -f $TOOLKIT/busybox_installed ]; then
    chmod 777 $TOOLKIT
    busybox --install -s $TOOLKIT
    echo > $TOOLKIT/busybox_installed
fi
