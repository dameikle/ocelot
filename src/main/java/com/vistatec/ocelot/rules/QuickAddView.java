package com.vistatec.ocelot.rules;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;

import com.google.common.eventbus.EventBus;
import com.vistatec.ocelot.ui.ODialogPanel;

public class QuickAddView extends ODialogPanel {
    private static final long serialVersionUID = 1L;
    private QuickAddViewTable quickAddTable;

    public QuickAddView(RuleConfiguration ruleConfig, EventBus eventBus) {
        super(new GridBagLayout());
        setBorder(new EmptyBorder(10,10,10,10));

        GridBagConstraints gridBag = new GridBagConstraints();
        gridBag.anchor = GridBagConstraints.FIRST_LINE_START;
        gridBag.gridwidth = 1;
        int gridy = 0;

        JLabel title = new JLabel("Double-click a rule to add it to the selected segment.");
        gridBag.gridx = 0;
        gridBag.gridy = gridy++;
        add(title, gridBag);

        quickAddTable = new QuickAddViewTable(ruleConfig, eventBus);
        gridBag = new GridBagConstraints();
        gridBag.gridx = 0;
        gridBag.gridy = gridy++;
        gridBag.gridwidth = GridBagConstraints.REMAINDER;
        gridBag.weightx = 1.0;
        gridBag.weighty = 1.0;
        gridBag.fill = GridBagConstraints.HORIZONTAL;
        gridBag.insets = new Insets(10, 10, 10, 10);
        add(quickAddTable.getTable(), gridBag);
    }
}