package com.poc.elasticsearch.web;

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
public class ProductPageController {

    private final ProductService productService;

    public ProductPageController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/products";
    }

    @GetMapping("/products")
    public String products(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            Model model
    ) {
        if (!model.containsAttribute("productForm")) {
            model.addAttribute("productForm", new ProductForm());
        }
        model.addAttribute("products", productService.search(q, category));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("category", category == null ? "" : category);
        model.addAttribute("searchActive", hasValue(q) || hasValue(category));
        return "products";
    }

    @PostMapping("/products")
    public String createProduct(
            @Valid @ModelAttribute("productForm") ProductForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("products", productService.findAll());
            model.addAttribute("q", "");
            model.addAttribute("category", "");
            model.addAttribute("searchActive", false);
            return "products";
        }

        productService.create(form);
        redirectAttributes.addFlashAttribute("successMessage", "Product added successfully.");
        return "redirect:/products";
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
