# fscan

Filescan Tool 

The primary feature of this tool is to locate duplicate files and interactively delete them based on User input.

This is alpha software.

## Installation

Installer https://fscan.niklauszbinden.ch

Binary download: 

    $ wget -q https://nickik.keybase.pub/fscan/fscan
    
## Usage

Run the project directly:

    $ clj -m nickik.filescan

Build Binary (adjust to your installation)

    $ export GRAALVM_HOME=/usr/lib/jvm/graalvm 
    $ clj -A:cambada:native-image -m nickik.filescan
    $ ./target/filescan --help
    
## CLI Example

    $ fscan --help
    
## Options

Everything you need:

    $ fscan --help

## Examples

    $ fscan --help

## License

Copyright © 2019 Niklaus Zbinden

Released under MIT License
