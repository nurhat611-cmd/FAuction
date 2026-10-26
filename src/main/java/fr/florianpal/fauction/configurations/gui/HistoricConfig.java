package fr.florianpal.fauction.configurations.gui;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.FAuction;

public class HistoricConfig extends AbstractGuiWithAuctionsConfig {

    public void load(FAuction plugin, YamlDocument config) {
        super.load(plugin, config, "historic");
    }
}
