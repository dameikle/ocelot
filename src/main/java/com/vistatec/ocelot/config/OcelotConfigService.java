package com.vistatec.ocelot.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vistatec.ocelot.config.xml.RootConfig;
import com.vistatec.ocelot.config.xml.PluginConfig;

import com.vistatec.ocelot.config.xml.ProvenanceConfig;
import com.vistatec.ocelot.config.xml.TmManagement;
import com.vistatec.ocelot.plugins.Plugin;

/**
 * Service for reading/saving configuration values.
 */
public class OcelotConfigService implements ConfigService {
    private static final Logger LOG = LoggerFactory.getLogger(OcelotConfigService.class);

    private final ConfigTransferService cfgXservice;
    private RootConfig config;

    public OcelotConfigService(ConfigTransferService cfgXService) throws ConfigTransferService.TransferException {
        this.cfgXservice = cfgXService;
        this.config = cfgXService.parse();
    }

    @Override
    public void saveConfig() throws ConfigTransferService.TransferException {
        cfgXservice.save(config);
    }

    @Override
    public boolean wasPluginEnabled(Plugin plugin) {
        PluginConfig pcfg = findPluginConfig(plugin);
        return pcfg.getEnabled();
    }

    @Override
    public void enablePlugin(Plugin plugin, boolean enabled) {
        PluginConfig pcfg = findPluginConfig(plugin);
        pcfg.setEnabled(enabled);
    }

    @Override
    public void savePluginEnabled(Plugin plugin, boolean enabled) throws ConfigTransferService.TransferException {
        enablePlugin(plugin, enabled);
        cfgXservice.save(config);
    }

    @Override
    public PluginConfig findPluginConfig(Plugin plugin) {
        PluginConfig foundPluginConfig = null;
        for (PluginConfig pcfg : config.getPlugins()) {
            if (pcfg.matches(plugin)) {
                foundPluginConfig = pcfg;
            }
        }

        if (foundPluginConfig == null) {
            foundPluginConfig = new PluginConfig(plugin, false);
            config.getPlugins().add(foundPluginConfig);
        }
        return foundPluginConfig;
    }

    @Override
    public UserProvenance getUserProvenance() {
        return new UserProvenance(config.getUserProvenance().getRevPerson(),
                config.getUserProvenance().getRevOrganization(),
                config.getUserProvenance().getExternalReference());
    }

    @Override
    public void saveUserProvenance(UserProvenance prov) throws ConfigTransferService.TransferException {
        ProvenanceConfig pConfig = config.getUserProvenance();
        pConfig.setRevPerson(prov.getRevPerson());
        pConfig.setRevOrganization(prov.getRevOrg());
        pConfig.setExternalReference(prov.getProvRef());
        cfgXservice.save(config);
    }

    @Override
    public double getFuzzyThreshold() {
        return config.getTmManagement().getFuzzyThreshold();
    }

    @Override
    public void saveFuzzyThreshold(float threshold) throws ConfigTransferService.TransferException {
        config.getTmManagement().setFuzzyThreshold(threshold);
        cfgXservice.save(config);
    }

    @Override
    public int getMaxResults() {
        return config.getTmManagement().getMaxResults();
    }

    @Override
    public void saveMaxResults(int maxResults) throws ConfigTransferService.TransferException {
        config.getTmManagement().setMaxResults(maxResults);
        cfgXservice.save(config);
    }

    @Override
    public List<TmManagement.TmConfig> getTms() {
        return config.getTmManagement().getTms();
    }

    @Override
    public void saveTms(List<TmManagement.TmConfig> tmConfig) throws ConfigTransferService.TransferException {
        config.getTmManagement().setTm(tmConfig);
        cfgXservice.save(config);
    }

    @Override
    public TmManagement.TmConfig getTmConfig(String tmName) {
        for (TmManagement.TmConfig tm : config.getTmManagement().getTms()) {
            if (tm.getTmName().equals(tmName)) {
                return tm;
            }
        }
        return null;
    }

    @Override
    public void enableTm(String tmName, boolean enable) throws ConfigTransferService.TransferException {
        TmManagement.TmConfig tmConfig = getTmConfig(tmName);
        if (tmConfig == null) {
            LOG.error("Missing TM configuration for '{}'", tmName);
            throw new IllegalStateException("Missing TM configuration for '"+tmName+"'");
        }
        tmConfig.setEnabled(enable);
        cfgXservice.save(config);
    }

    @Override
    public void saveTmDataDir(TmManagement.TmConfig tm, String tmDataDir) throws ConfigTransferService.TransferException {
        tm.setTmDataDir(tmDataDir);
        cfgXservice.save(config);
    }

    @Override
    public TmManagement.TmConfig createNewTmConfig(String tmName, boolean enabled, String tmDataDir) throws ConfigTransferService.TransferException {
        TmManagement.TmConfig newTmConfig = new TmManagement.TmConfig();
        newTmConfig.setTmName(tmName);
        newTmConfig.setEnabled(enabled);
        newTmConfig.setTmDataDir(tmDataDir);
        config.getTmManagement().getTms().add(newTmConfig);
        cfgXservice.save(config);
        return newTmConfig;
    }
}