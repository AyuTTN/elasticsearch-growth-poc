package com.poc.elasticsearch.web;

import com.poc.elasticsearch.exception.OrderPlacementException;
import com.poc.elasticsearch.model.OrderStatus;
import com.poc.elasticsearch.service.OrderService;
import com.poc.elasticsearch.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OrderPageController {

    private final OrderService orderService;
    private final ProductService productService;

    public OrderPageController(OrderService orderService, ProductService productService) {
        this.orderService = orderService;
        this.productService = productService;
    }

    @GetMapping("/orders")
    public String orders(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String status,
            Model model
    ) {
        if (!model.containsAttribute("orderForm")) {
            model.addAttribute("orderForm", new OrderForm());
        }
        populate(model, email, status);
        return "orders";
    }

    @PostMapping("/orders")
    public String placeOrder(
            @Valid @ModelAttribute("orderForm") OrderForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            populate(model, "", "");
            return "orders";
        }

        try {
            orderService.place(form);
        } catch (OrderPlacementException ex) {
            bindingResult.reject("order", ex.getMessage());
            populate(model, "", "");
            return "orders";
        }

        redirectAttributes.addFlashAttribute("successMessage", "Order placed. Stock was reduced in Elasticsearch.");
        return "redirect:/orders";
    }

    private void populate(Model model, String email, String status) {
        model.addAttribute("orders", orderService.search(email, status));
        model.addAttribute("products", productService.findAll());
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("email", email == null ? "" : email);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("searchActive", hasValue(email) || hasValue(status));
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
