package com.overkill.client.module.modules;

import com.overkill.client.module.Category;
import com.overkill.client.module.Module;

/** Applied by LightmapRenderStateExtractorMixin. The lightmap is rebuilt every tick, so turning it off needs no restore. */
public class Fullbright extends Module {
	public Fullbright() {
		super("Fullbright", "Everything fully lit: caves, night, the Nether.", Category.RENDER);
	}
}
