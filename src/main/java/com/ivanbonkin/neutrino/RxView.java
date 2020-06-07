package com.ivanbonkin.neutrino;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@UIScope
@SpringComponent
public class RxView extends Div {

    Receiver receiver;

    public RxView(@Autowired Receiver receiver, @Autowired ProgressView progressView) {
        this.receiver = receiver;

        ProgressBar progressBar = new ProgressBar();
        add(progressBar);
    }

}


