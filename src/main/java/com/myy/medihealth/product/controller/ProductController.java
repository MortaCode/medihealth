package com.myy.medihealth.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myy.medihealth.common.result.Result;
import com.myy.medihealth.product.entity.Product;
import com.myy.medihealth.product.service.ProductService;
import com.myy.medihealth.product.vo.ProductSaveVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("medical/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/list")
    public Result<IPage<Product>> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String keyword) {
        IPage<Product> result = productService.page(page, size, category, keyword);
        return Result.success(result);
    }

    @GetMapping("/detail/{id}")
    public Result<Product> detail(@PathVariable String id) {
        Product product = productService.detail(id);
        return Result.success(product);
    }

    @PostMapping("/create")
    public Result<Product> create(@RequestBody ProductSaveVo vo) {
        Product product = productService.create(vo);
        return Result.success(product);
    }

    @PutMapping("/update/{id}")
    public Result<Product> update(@PathVariable String id, @RequestBody ProductSaveVo vo) {
        Product product = productService.update(id, vo);
        return Result.success(product);
    }

    @PutMapping("/off/{id}")
    public Result<String> offShelf(@PathVariable String id) {
        productService.offShelf(id);
        return Result.success("下架成功");
    }
}
