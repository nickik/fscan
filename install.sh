#!/bin/bash

if [ -d "$HOME/bin" ]; then
	echo "fscan will be moved into your ~/bin folder. Make sure you add it to your \$PATH"
else
	echo "You don't have a ~/bin folder, will create one. Make sure to add it to your \$PATH"
	mkdir "$HOME/bin"
fi

wget -q https://nickik.keybase.pub/fscan/fscan

chmod +x fscan
mv fscan $HOME/bin

