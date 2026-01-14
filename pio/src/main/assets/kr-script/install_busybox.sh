#!/system/bin/sh

if [ ! -f $START_DIR/busybox_installed ]; then
    chmod 777 $TOOLKIT
    for vv in $(busybox --list); do
        if [ ! -e $TOOLKIT/$vv ];then
            echo "create: $vv"
            ln -sf $TOOLKIT/busybox $TOOLKIT/$vv
        fi
    done
    echo > $START_DIR/busybox_installed
fi

sleep 1
