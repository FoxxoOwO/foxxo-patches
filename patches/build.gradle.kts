group = "app.morphe"

patches {
    about {
        name        = "AI Plant Doctor Patches"
        description = "Patches for AI Plant Doctor (me.jodoin.aiplantdoctor)"
        source      = "local"
        author      = "Morphe User"
        contact     = "na"
        website     = "https://morphe.software"
        license     = "GNU General Public License v3.0"
    }
}

dependencies {
    implementation(libs.bundles.patcher)
}
