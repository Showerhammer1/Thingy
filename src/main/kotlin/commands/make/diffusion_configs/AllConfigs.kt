package commands.make.diffusion_configs

val diffusionConfigs = mapOf(
    "standardSmall" to (standardSmall to "Standard"),
    "pixelArtHard" to (pixelArtHard to "Pixel art (hard)"),
    "pixelArtSoft" to (pixelArtSoft to "Pixel art (soft)")
)
val diffusionConfigInstanceToName = diffusionConfigs.keys.associateBy({
    diffusionConfigs[it]!!.first
}) {
    diffusionConfigs[it]!!.second
}