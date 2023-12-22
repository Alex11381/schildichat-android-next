# schildichat-android-next

Matrix client based on [Element Android X](https://github.com/vector-im/element-x-android)
in the tradition of the original [SchildiChat for Android](https://github.com/SchildiChat/SchildiChat-android)
which was based on the now deprecated Element Android codebase.

Similarly to Element X, this SchildiChat Android rewrite is still in alpha state and lacks most original features,
and hasn't even been entirely rebranded yet.

<a href="https://s2.spiritcroc.de/testing/fdroid/repo/" alt="Get it on F-Droid" target="_blank"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="50"></a>


## Translations

TODO


## Screenshots

TODO - I should look into fastlane or something to generate these automatically this time


## Building

In general, building works the same as for Element X or any common Android project.
Just import into Android Studio and make sure you have all the required SDKs ready.

Currently, SchildiChat uses a [forked matrix-rust-sdk](https://github.com/SchildiChat/matrix-rust-sdk)
published on [GitHub packages](https://github.com/SchildiChat/matrix-rust-components-kotlin/packages/),
which unfortunately [still does not provide unauthenticated access](https://github.com/orgs/community/discussions/26634).
Accordingly, before building, create a GitHub token with the `read:packages` permission, and configure it in your `local.properties`:
```
gpr.user=...
gpr.token=...
```

Alternatively, you may export the `GPR_USER` and `GPR_TOKEN` environment variables before building.

If you do not have a GitHub account, you can also download the appropriate `.aar` file from
[here](https://github.com/SchildiChat/matrix-rust-components-kotlin/releases) and put it into `./libraries/rustsdk/matrix-rust-sdk.aar`.


## Contributing

Generally, contributions are welcome!  
Note that in order to ease upstream merges, we want to leave the smallest footprint possible on Element's sources
when implementing original features or patching Element's behaviour.

In particular (may change a bit while the project is still in alpha):
- Put code into additional files (`chat.schildi.*` package names) if reasonable
    - Prefer `schildi/lib` module if it doesn't depend on element modules (except maybe strings)
    - Prefer `schildi/components` module if it depend on some of Element's Design/UI components but nothing else
    - Otherwise, prefer element module where it makes most sense (or create a new module for bigger features, maybe)
- Put Schildi-specific drawables and other xml resources that override upstream resources into the `sc` build flavor
    - This way, we can use the same name and avoid merge conflicts
    - Compare e.g. `libraries/designsystem`: we define the flavor `sc` and thus put drawables in the `sc` instead of `main` directory.
      For new modules that do not feature a `sc` flavor yet, copy over the required `build.gradle.kts` content from a module that does.
- Put Schildi-specific strings into `schildi/lib`
    - Never touch upstream strings! If we want to change Element's strings, we'll either want a script that patches them,
      so we can restore upstream strings before upstream merges and re-do our changes automatically after the merge,
      or alternative put it into an `sc` build flavor, if we do not need to touch multiple translations.
- Don't worry too much about code style if violating it can ease upstream merges
    - When putting upstream code into a new block (e.g., putting it in an `if`-statement), don't indent the upstream code, but rather add comments like
        `// Wrong indention for merge-ability - start` and `// Wrong indention for merge-ability - end`
