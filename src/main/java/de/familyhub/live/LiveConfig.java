package de.familyhub.live;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Scheduling für den Herzschlag der Live-Verbindungen (LiveUpdates.heartbeat)
@Configuration
@EnableScheduling
public class LiveConfig implements WebMvcConfigurer {

    private final LiveUpdates live;

    public LiveConfig(LiveUpdates live) {
        this.live = live;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ChangeNotifier(live)).addPathPatterns("/api/**");
    }
}
