package com.elemental.client.renderer;

import com.elemental.client.model.FrostwakePickModel;
import com.elemental.item.FrostwakePickItem;

public class FrostwakePickRenderer extends ElementalWeaponRenderer<FrostwakePickItem> {
    public FrostwakePickRenderer() {
        super(new FrostwakePickModel());
    }
}