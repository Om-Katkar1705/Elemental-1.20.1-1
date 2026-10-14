package com.elemental.client.renderer;

import com.elemental.client.model.StormcallerHammerModel;
import com.elemental.item.StormcallerHammerItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class StormcallerHammerRenderer extends GeoItemRenderer<StormcallerHammerItem> {
    public StormcallerHammerRenderer() {
        super(new StormcallerHammerModel());
    }
}
