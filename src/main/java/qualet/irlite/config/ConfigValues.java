package qualet.irlite.config;

import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

/** CML owns persistence; this instance owns bindings to its registered Value settings. */
public final class ConfigValues
{
    public ValueBoolean showGuides;
    public ValueInt shadowQuality;
    public ValueBoolean shadowBlocks;
    public ValueInt maxShaderLights;
    public ValueFloat vlIntensity;
    public ValueInt vlSteps;
    public ValueFloat vlMaxDist;
    public ValueBoolean vlShadowsLive;
    public ValueInt vlShadowStride;
    public ValueFloat vlTipBoost;
    public ValueFloat vlTipRadius;
    public ValueBoolean vlNoiseLive;
    public ValueFloat vlNoiseAmount;
    public ValueFloat vlNoiseScale;
    public ValueFloat vlNoiseSpeed;
    public ValueFloat vlNoiseMorph;
    public ValueInt vlNoiseStride;
    public ValueBoolean vlBlueNoise;
    public ValueBoolean vlDitherTemporal;
    public ValueBoolean vlClusterCull;
    public ValueBoolean vlShadowHiz;
    public ValueBoolean outline;
    public ValueInt outlineTarget;
    public ValueFloat outlineStrength;
    public ValueInt outlinePixelSize;
    public ValueFloat outlineFresnelPower;
    public ValueFloat outlineBack;
    public ValueBoolean outlineFront;
    public ValueFloat outlineFrontStrength;
    public ValueBoolean outlineGlow;
    public ValueFloat outlineGlowStrength;
    public ValueBoolean shadowsLive;
    public ValueFloat shadowSoftness;
    public ValueBoolean shadowPartialTile;
    public ValueBoolean diffuse;
    public ValueFloat intensity;
    public ValueBoolean specular;
    public ValueFloat specularIntensity;
    public ValueBoolean toon;
    public ValueInt toonBands;
    public ValueFloat toonSmooth;
}
