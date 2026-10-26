package com.elemental.client.renderer;

import com.elemental.client.model.AshenRiftBladeModel;
import com.elemental.item.AshenRiftBladeItem;

public class AshenRiftBladeRenderer extends ElementalWeaponRenderer<AshenRiftBladeItem> {
    public AshenRiftBladeRenderer() {
        super(new AshenRiftBladeModel());
    }
}
