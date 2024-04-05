# pscextract

extract mongodb collection into file, is also a webserver that exposes the file for download

## Developement

### Distribution history

This ecosystem uses many independant components, some of which live an independant life in distinct repositories.
For each release of `psc-extrract`, [the psc-components' distribution  history](https://github.com/ansforge/psc-components/blob/main/DISTRIBUTION.md) 
file will need to be updated with the new version, so that we can keep track of compatible component versions, 
and go back to a previous working distribution if need be.

### Release procedure

Whenever a version is ready for release, run the following commands on the `main` branch (or on the maintenance branch if we're about to issue a production FIX). This should run on any shell, be it `bash`, `cmd` or if needed `gitbash`.

```bash
mvn release:prepare -DautoVersionSubmodules=true -DtagNameFormat=@{version}
git push
git push origin <new_version_tag>
```

where `<new_version_tag>` stands for the new version.

Eg to relase `1.0.2` :

```bash
mvn release:prepare -DautoVersionSubmodules=true -DtagNameFormat=@{version}
git push
git push origin 0.0.2
```
