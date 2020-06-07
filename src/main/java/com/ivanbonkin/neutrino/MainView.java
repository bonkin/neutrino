package com.ivanbonkin.neutrino;


import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Route;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Push
@Route
public class MainView extends VerticalLayout {

    public MainView(
            @Autowired RxView rxView,
            @Autowired TxView txView,
            @Autowired ProgressView progressView) {

        add(progressView);

        Tabs tabs = new Tabs();

        Tab rx = new Tab("Rx");
        rx.setSelected(true);
        rx.setFlexGrow(1);

        Tab tx = new Tab("Tx");
        tx.setSelected(false);
        txView.setVisible(false);
        tx.setFlexGrow(1);

        tabs.add(rx, tx);
        tabs.addSelectedChangeListener(e -> getUI().ifPresent(ui -> {
            Div[] divs = {rxView, txView};
            divs[tabs.getSelectedIndex() ^ 1].setVisible(false);
            divs[tabs.getSelectedIndex()].setVisible(true);
        }));
        tabs.setSizeFull();
        add(tabs);

        add(rxView);
        add(txView);
    }

}


