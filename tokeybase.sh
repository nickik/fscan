#!/bin/sh

mv target/filescan target/fscan

BASE=/keybase/public/nickik/fscan
rm -v $BASE/fscan
rm -v $BASE/install.sh
cp -v target/fscan $BASE
cp -v install.sh $BASE
