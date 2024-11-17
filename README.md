# mu format 
[![Project Status: WIP – Initial development is in progress, but there has not yet been a stable, usable release suitable for the public.](https://www.repostatus.org/badges/latest/wip.svg)](https://www.repostatus.org/#wip)

manage your music collection without touching any media files

* non destructive
* flat file
  * git friendly
 
## unsorted (and possibly useless) notes

### Types

* `=artist`
  * default attributes
    * `artist` name of containing directory
* `=release`
  * default attributes
    * `album-title` name of containing directory
* `=wrapper`
* `=shadow`

###  Artist Attributes
* `name`
  * *single*
* `alias`
  * *multiple*
* `is-group`
  * *flag*
* `member`
  * *multiple*

### Release Attributes
* `title`
* `bit-depth`
* `bitrate`
* `discogs-master-id`
* `discogs-release-id`
* `release-year`
* `release-year-medium`
* `rip-result`
* `source-medium`
* `source-store`
* `sample-rate`
* `title`

### =shadow example

```
Kuhn Fu
└── Katastrofik Kink Machine [2023, BC]
    ├── =mu
    │   ├── =shadow.mu
    │   └── KUHN FU - Katastrofik Kink Machine
    │       ├── =release.mu
    │       ├── bit-depth=24.mu
    │       ├── discogs-master-id=3631851.mu
    │       ├── release-year=2023.mu
    │       ├── sample-rate=44100.mu
    │       ├── source-medium=File.mu
    │       ├── source-store=Bandcamp.mu
    │       └── title=Katastrofik Kink Machine.mu
    └── KUHN FU - Katastrofik Kink Machine
        ├── KUHN FU - Katastrofik Kink Machine - 01 Waffle House.m4a
        ├── KUHN FU - Katastrofik Kink Machine - 02 Low and Slow.m4a
        ├── KUHN FU - Katastrofik Kink Machine - 03 Grande False.m4a
        ├── KUHN FU - Katastrofik Kink Machine - 04 Die ID.m4a
        ├── KUHN FU - Katastrofik Kink Machine - 05 Kink.m4a
        ├── KUHN FU - Katastrofik Kink Machine - 06 Enigma.m4a
        ├── KUHN FU - Katastrofik Kink Machine - 07 Simple & Charming.m4a
        └── cover.jpg
```

### `tree .`

```
Overmono
├── =mu
│   ├── =artist.mu
│   └── name=Overmono.mu
├── Arla I
│   ├── =mu
│   │   ├── bit-depth=16.mu
│   │   ├── channel-mode=stereo.mu
│   │   ├── discogs-master=m1148038.mu
│   │   ├── release-year=2016.mu
│   │   ├── sample-rate=44100.mu
│   │   ├── source-details=7digital.mu
│   │   ├── source=web.mu
│   │   └── title=Arla I.mu
│   ├── Overmono - 01. Olchon Flows [CE716329].flac
│   ├── Overmono - 02. Lockner Union [8DB03CE3].flac
│   ├── Overmono - 03. C-Life [C33053A5].flac
│   ├── Overmono - 04. Winged [DB7B34BA].flac
│   └── Overmono - 05. Programmer [C6A7C07C].flac
├── Arla II
│   ├── =mu
│   │   ├── bit-depth=16.mu
│   │   ├── release-year=2017.mu
│   │   ├── sample-rate=44100.mu
│   │   ├── source-details=7digital.mu
│   │   ├── source=web.mu
│   │   └── title=Arla II.mu
│   ├── Overmono - 01. O-Coast [526F1229].flac
│   ├── Overmono - 02. Telephax 030 [B154DDAC].flac
│   ├── Overmono - 03. HR3 [0CC793CA].flac
│   ├── Overmono - 04. 16 Steps [3F633458].flac
│   ├── Overmono - 05. Concorde [881E6068].flac
│   └── Overmono - 06. Powder Dry [26174CB7].flac
├── Fabric Presents Overmono
│   ├── =mu
│   │   ├── bit-depth=16
│   │   ├── discogs-release-id=19510660
│   │   ├── release-year-medium=2021
│   │   ├── release-year=2021
│   │   ├── rip-result=accurate
│   │   ├── sample-rate=44000
│   │   └── source=cd
│   ├── 01 So U Kno [613048DC].m4a
│   ├── 02 The Soul [9CB03AAB].m4a
│   ├── 03 Moonraker [F70EFCE2].m4a
│   ├── 04 Billy Hologram [5640DBD5].m4a
│   ├── 05 Hyperfunk [EFBD1F18].m4a
│   ├── 06 If U Ever [47300666].m4a
│   ├── 07 Pegassans [25EE2DD9].m4a
│   ├── 08 Sound Pressure Part 3 [EAB68C54].m4a
│   ├── 09 4AM At The Crying Cactus [276F9ED8].m4a
│   ├── 10 I Have A Dream [87C212C1].m4a
│   ├── 11 Fuk [4AB2A46C].m4a
│   ├── 12  I Have A Love (Overmono Remix) [3E952F90].m4a
│   ├── 13 Victim [6BDCE750].m4a
│   ├── 14 Lost Of Light [6016CB5E].m4a
│   ├── 15 BMW Track [0136C71E].m4a
│   ├── 16 Shhh [7C253F25].m4a
│   ├── 17 Pop Pop [4EEF2179].m4a
│   ├── 18 Bromine [1F148BD9].m4a
│   ├── 19 Morphing Into Brighter [4738544C].m4a
│   ├── 20 Caves Of Paradise [159DD226].m4a
│   ├── 21 Thunderclap (Dubplate Mix) [734C304C].m4a
│   ├── 22 A Rabbit Spoke To Me When I Woke Up [08433783].m4a
│   ├── 23 Bacteria [3ADD971D].m4a
│   ├── 24 Intellect [F1253E27].m4a
│   ├── 25 Film Score [8FD1C9BF].m4a
│   ├── 26 Fourth Dimensional [84FA41C3].m4a
│   ├── 27 Erolfa [5C94AD90].m4a
│   ├── 28 When I Close My Eyes I See Paint [D5349315].m4a
│   ├── 29 Get 2 Kno [F53AE7D6].m4a
│   └── Secure Ripping Log.txt
├── Good Lies
│   ├── =mu
│   │   ├── =mu-release.mu
│   │   ├── bit-depth=16.mu
│   │   ├── discogs-release-id=27033375.mu
│   │   ├── release-year-medium=2023.mu
│   │   ├── release-year=2023.mu
│   │   ├── rip-result=accurate.mu
│   │   ├── sample-rate=44000.mu
│   │   ├── source-medium=cd.mu
│   │   ├── title=Good Lies.mu
│   │   └── type=album.mu
│   ├── 01 Feeling Plain [169570DC].m4a
│   ├── 02 Arla Fearn [59FA91FF].m4a
│   ├── 03 Good Lies [F67C1600].m4a
│   ├── 04 Walk Thru Water [A7997167].m4a
│   ├── 05 Cold Blooded [0DCBE928].m4a
│   ├── 06 Skulled [5BAE7577].m4a
│   ├── 07 Sugarrushhh [286B9031].m4a
│   ├── 08 Calon [C4C64843].m4a
│   ├── 09 Is U [28B214CC].m4a
│   ├── 10 Vermonly [4873C639].m4a
│   ├── 11 So U Kno [086963D5].m4a
│   ├── 12 Calling Out [91A04AB6].m4a
│   └── Secure Ripping Log.txt
```