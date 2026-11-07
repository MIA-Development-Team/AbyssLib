# Third-party notices

The root [LICENSE](LICENSE) covers AbyssLib's original code under the MIT
License. The third-party code below retains its own copyright and license.

| Module | Upstream source | License and attribution |
| --- | --- | --- |
| Reginth | [Registrate](https://github.com/tterrag1098/Registrate/tree/1.21/dev), `MC1.21-1.3.0+67`; tterrag1098 and contributors | [MPL-2.0](module.reginth/src/main/resources/META-INF/licenses/Registrate-MPL-2.0.txt) · [source scope](module.reginth/src/main/java/com/altnoir/abysslib/reginth/LICENSE) |
| ReLink | [Athena](https://github.com/terrarium-earth/Athena/tree/1.21.1), `4.0.6` for Minecraft 1.21.1; ThatGravyBoat and Terrarium Earth contributors | [MIT](module.relink/src/main/resources/META-INF/licenses/Athena-MIT.txt) · Copyright (c) 2024 Terrarium Earth |

Reginth incorporates Registrate source from the upstream sources JAR. The code
was moved from `com.tterrag.registrate` to `com.altnoir.abysslib.reginth`, its
registration entry points were renamed, and the implementation was adapted for
AbyssLib's registration and creative-tab APIs. The Registrate-derived files and
their modifications remain under MPL-2.0.

ReLink incorporates Athena's common and NeoForge model loading, connected
textures, rendering adapters, and related mixins. AbyssLib relocated the code
to its own packages, uses the `relink:` namespace with Athena format
compatibility, and adds emissive model support.

The Reginth and ReLink binary and source JARs carry component notices and
license texts in `META-INF/`. The aggregate AbyssLib binary JAR includes those
modules under `META-INF/jarjar/` and has its own notice pointing to them.

## Corresponding Reginth source

Use the Reginth `-sources.jar` with the same version as the Reginth binary.
For the aggregate JAR, use the version of its nested Reginth JAR.

- Maven: `com.altnoir:abysslib-reginth-1.21.1:<version>:sources`
- Release repository: https://maven.ringlo.im/releases/com/altnoir/abysslib-reginth-1.21.1/
- Development source: https://github.com/MIA-Development-Team/AbyssLib/tree/1.21.1-NeoForge/module.reginth

When redistributing the binary outside Maven, provide the matching source JAR
alongside it or tell recipients how to obtain that exact source version.
