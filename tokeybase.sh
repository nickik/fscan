#!/bin/sh

mv target/filescan target/fscan

BASE=/keybase/public/nickik/fscan
rm -v $BASE/fscan
cp -v target/fscan $BASE/fscan
