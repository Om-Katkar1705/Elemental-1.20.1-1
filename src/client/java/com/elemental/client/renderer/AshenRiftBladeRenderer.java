package com.elemental.client.renderer;

import com.elemental.client.model.AshenRiftBladeModel;
import com.elemental.item.AshenRiftBladeItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class AshenRiftBladeRenderer extends GeoItemRenderer<AshenRiftBladeItem> {
    public AshenRiftBladeRenderer() {
        super(new AshenRiftBladeModel());
    }
}
