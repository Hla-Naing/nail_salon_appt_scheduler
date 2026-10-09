package com.example.nail_salon_appt_scheduler;

import java.time.LocalDate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/web")
public class WebController {
    private final SalonService salon;
    private final AuthService auth;
    private final SessionAuthService sessions;
    private final AppointmentService appointments;
    private final ProviderService providers;
    private final SalonTime time;
    public WebController(SalonService salon, AuthService auth, SessionAuthService sessions,
                         AppointmentService appointments, ProviderService providers, SalonTime time) {
        this.salon = salon; this.auth = auth; this.sessions = sessions;
        this.appointments = appointments; this.providers = providers; this.time = time;
    }

    // Only safe public account fields are ever placed in the HTML model.
    public record Viewer(String name, String role) {}
    @ModelAttribute("viewer")
    public Viewer viewer(HttpServletRequest request) {
        if (request.getSession(false) == null || request.getSession(false).getAttribute("userId") == null) return null;
        try {
            UserAccount user = sessions.requireUser(request);
            return new Viewer(user.name(), user.role());
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() != 401) throw e;
            request.getSession(false).removeAttribute("userId");
            return null;
        }
    }

    @GetMapping({"", "/"})
    public String home(Model model) { model.addAttribute("salon", salon.getHome()); return "home"; }

    @GetMapping("/login")
    public String login() { return "login"; }

    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password,
                        HttpServletRequest request, HttpServletResponse response, Model model) {
        try {
            UserAccount user = auth.authenticate(username, password);
            sessions.login(request, user);
            return "redirect:/web/" + (user.role().equals("PROVIDER") ? "provider" : "appointments");
        } catch (ResponseStatusException e) {
            response.setStatus(e.getStatusCode().value());
            model.addAttribute("message", e.getReason());
            return "login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return "redirect:/web";
    }

    @GetMapping("/slots")
    public String slots(@RequestParam(required = false) Long providerId,
                        @RequestParam(required = false) Long serviceId,
                        @RequestParam(required = false) LocalDate date,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size, Model model) {
        var slots = salon.getAvailableSlots(providerId, serviceId, date, page, size);
        model.addAttribute("slots", slots);
        model.addAttribute("providers", salon.providers());
        model.addAttribute("services", salon.services());
        model.addAttribute("providerId", providerId); model.addAttribute("serviceId", serviceId);
        model.addAttribute("date", date); model.addAttribute("page", page); model.addAttribute("size", size);
        model.addAttribute("hasNext", slots.size() == size && page < Integer.MAX_VALUE);
        return "slots";
    }

    @PostMapping("/book")
    public String book(@RequestParam Long slotId, HttpServletRequest request) {
        Long customer = sessions.requireRole(request, "CUSTOMER").userId();
        Long id = appointments.bookAppointment(customer, slotId);
        return "redirect:/web/confirmation/" + id;
    }

    @GetMapping("/confirmation/{appointmentId}")
    public String confirmation(@PathVariable Long appointmentId, HttpServletRequest request, Model model) {
        Long customer = sessions.requireRole(request, "CUSTOMER").userId();
        var appointment = appointments.getCustomerAppointments(customer).stream()
                .filter(a -> a.appointmentId().equals(appointmentId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        model.addAttribute("appointment", appointment);
        return "confirmation";
    }

    @GetMapping("/appointments")
    public String appointments(HttpServletRequest request, Model model) {
        model.addAttribute("appointments", appointments.getCustomerAppointments(sessions.requireRole(request, "CUSTOMER").userId()));
        return "appointments";
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    public String cancel(@PathVariable Long appointmentId, HttpServletRequest request, RedirectAttributes flash) {
        var fee = appointments.cancelAppointment(sessions.requireRole(request, "CUSTOMER").userId(), appointmentId);
        flash.addFlashAttribute("message", "Appointment cancelled. Fee charged: $" + fee.toPlainString());
        return "redirect:/web/appointments";
    }

    @GetMapping("/provider")
    public String provider(HttpServletRequest request, Model model) {
        Long user = sessions.requireRole(request, "PROVIDER").userId();
        model.addAttribute("appointments", providers.appointments(user));
        model.addAttribute("slots", providers.slots(user));
        model.addAttribute("services", salon.services());
        return "provider";
    }

    @PostMapping("/provider/slots")
    public String create(@RequestParam Long serviceId, @RequestParam String startAt, @RequestParam String endAt,
                         HttpServletRequest request, RedirectAttributes flash) {
        Long user = sessions.requireRole(request, "PROVIDER").userId();
        providers.createSlot(user, serviceId, time.parse(startAt), time.parse(endAt));
        flash.addFlashAttribute("message", "Availability created");
        return "redirect:/web/provider";
    }

    @PostMapping("/provider/slots/{slotId}/remove")
    public String remove(@PathVariable Long slotId, HttpServletRequest request, RedirectAttributes flash) {
        providers.removeSlot(sessions.requireRole(request, "PROVIDER").userId(), slotId);
        flash.addFlashAttribute("message", "Availability removed");
        return "redirect:/web/provider";
    }

    @ExceptionHandler(ResponseStatusException.class)
    public String error(ResponseStatusException e, HttpServletResponse response, Model model) {
        response.setStatus(e.getStatusCode().value());
        model.addAttribute("message", e.getReason());
        return "web-error";
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public String invalid(Exception e, HttpServletResponse response, Model model) {
        response.setStatus(400);
        model.addAttribute("message", "Check the required fields, IDs and dates, then try again.");
        return "web-error";
    }
}
