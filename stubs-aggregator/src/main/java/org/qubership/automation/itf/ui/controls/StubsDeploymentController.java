package org.qubership.automation.itf.ui.controls;

import org.qubership.automation.itf.ui.service.TriggerRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StubsDeploymentController {

    /**
     * ApplicationAvailability object.
     */
    private final ApplicationAvailability applicationAvailability;
    private final TriggerRouteService triggerRouteService;

    /**
     * Constructor.
     *
     * @param applicationAvailability ApplicationAvailability object.
     */
    @Autowired
    public StubsDeploymentController(final ApplicationAvailability applicationAvailability,
                                     final TriggerRouteService triggerRouteService) {
        this.applicationAvailability = applicationAvailability;
        this.triggerRouteService = triggerRouteService;
    }

    /**
     * Return response for livenessProbe.
     *
     * @return ResponseEntity with OK (in case Correct liveness state) or INTERNAL_SERVER_ERROR HttpStatus.
     */
    @GetMapping("/rest/deployment/liveness")
    public ResponseEntity<Void> liveness() {
        return new ResponseEntity<>(
                LivenessState.CORRECT.equals(applicationAvailability.getLivenessState())
                        ? HttpStatus.OK
                        : HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Return response for readinessProbe.
     *
     * @return ResponseEntity with status depending on applicationAvailability.getReadinessState()
     * and triggerRouteService.isInitialHttpTriggersActivationCompleted().
     */
    @GetMapping("/rest/deployment/readiness")
    public ResponseEntity<Void> readiness() {
        return new ResponseEntity<>(
                ReadinessState.ACCEPTING_TRAFFIC.equals(applicationAvailability.getReadinessState())
                        && triggerRouteService.isInitialHttpTriggersActivationCompleted()
                        ? HttpStatus.OK
                        : HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
