<!--
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Inherited Anki star: copyright and licensing

Use these licensing entries for the Anki star and its AnkiDroid adaptations.

<!-- REUSE-IgnoreStart -->
```text
SPDX-License-Identifier: AGPL-3.0-or-later
SPDX-FileComment: Inherited Anki star, copyright Alex Fraser.

SPDX-License-Identifier: GPL-3.0-or-later
SPDX-FileComment: AnkiDroid adaptations, where present.

SPDX-FileComment: See docs/licenses/ANKI_STAR_LICENSE.md
```
<!-- REUSE-IgnoreEnd -->

![AnkiDroid adaptation of the inherited Anki star](../graphics/license-snapshots/ankidroid-star-f6267972c9.png)

AnkiDroid's star adaptation, copied from [f6267972c9](https://github.com/ankidroid/Anki-Android/blob/f6267972c97577cb82bd2857f4f81dfb04b7742e/AnkiDroid/src/main/res/drawable-mdpi/logo_star_144dp.png).

> [!NOTE]
> For the full AnkiDroid logo, see [LOGO_LICENSE.md](LOGO_LICENSE.md).

## Timeline

| Date | Event | Source |
| --- | --- | --- |
| 21 December 2012 | Anki's monorepo import includes the logo, crediting Alex Fraser and recording AGPL3 terms with limited publication permissions. | [Anki import](https://github.com/ankitects/anki/commit/d477df8c28783c1d242b006473563f7cb2abc07d), [LICENSE.logo](https://github.com/ankitects/anki/blob/d477df8c28783c1d242b006473563f7cb2abc07d/LICENSE.logo) |
| 3 January 2013 | AnkiDroid imports the legacy icon, retaining the Anki star. | [AnkiDroid import](https://github.com/ankidroid/Anki-Android/commit/f4ac67379d4e6cc24a5f7b825eac03b7300a8bdb) |
| October 2015 | Alex Fraser confirms that he designed the original Anki icons. | [Alex's statement](https://groups.google.com/g/anki-android/c/yUJQWSEWfNQ) |
| November 2015 | Tim Rae's final-icon proposal records copying the star while making the design less rounded. | [Tim's proposal](https://groups.google.com/g/anki-android/c/yUJQWSEWfNQ/m/AyqfYW9_FAAJ) |
| 6 September 2017 | Anki clarifies the AGPL3 terms and the limited alternative licence for publications. | [Anki licence clarification](https://github.com/ankitects/anki/commit/9858ab3dbe4f566b5b44e46e772a0aebaa73fc07) |
| 3 January 2020 | Anki consolidates the logo's copyright attribution and licensing terms into its root LICENSE file. | [Anki licence consolidation](https://github.com/ankitects/anki/commit/8007113d7c08a10a3f0440be77c63caedc7c15dd) |

## Provenance

Anki's [2015 logo licence](https://github.com/ankitects/anki/blob/47940680d27dca0c2f4bca2acd83630414c56db3/LICENSE.logo)
and [upstream licence](https://github.com/ankitects/anki/blob/1b48429de2d188e64fce46c9635620e3767a6879/LICENSE#L23-L41)
attribute the inherited artwork to Alex Fraser. The latter provides
[AGPL version 3 or later](https://spdx.org/licenses/AGPL-3.0-or-later.html).
AnkiDroid adaptations retain their [GPL-3.0-or-later](../../LICENSES/GPL-3.0-or-later.txt)
terms. The alternative publication licence requires unmodified
artwork apart from proportional resizing; AnkiDroid's modified artwork uses
the copyleft terms.

The [original Anki star](https://github.com/ankitects/anki/blob/47940680d27dca0c2f4bca2acd83630414c56db3/designer/icons/anki.svg)
exactly matches star paths in [legacy/Icon.svg](../graphics/legacy/Icon.svg) and the
navigation-drawer sources. The 2015 outline has small coordinate changes;
the recorded copying and retained contours are the basis for treating it as
a derivative. Current SVGs and Android vectors retain those contours, with
scaling and rounding in the login variants.
