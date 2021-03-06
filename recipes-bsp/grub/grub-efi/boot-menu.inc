# Note the initrd command becomes not working if the command for
# loading image is changed to the chainloader command instead of
# the linux command.

menuentry "Sample EFI boot" {
    savedefault
    set fallback="Sample EFI boot (Recovery)"
    chainloader /bzImage root=/dev/sda2 ro rootwait initrd=/initrd
}

menuentry "Sample EFI boot (Recovery)" {
    chainloader /bzImage_bakup root=/dev/sda2 ro rootwait initrd=/initrd_bakup
}
