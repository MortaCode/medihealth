package com.myy.medihealth.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.myy.medihealth.common.result.Result;
import com.myy.medihealth.product.entity.Product;
import com.myy.medihealth.product.entity.ProductSpu;
import com.myy.medihealth.product.service.ProductService;
import com.myy.medihealth.product.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

/**
 * 商品控制器 — 大厂级 SPU/SKU 接口
 *
 * 列表：SPU 级商品卡（价格区间/变体标签）
 * 详情：SPU 级完整信息 + 所有 SKU 变体
 * SKU 切换：轻量返回单个 SKU 价格/库存
 */
@RestController
@RequestMapping("medical/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;


    /**
     * SPU 级商品列表（京东/淘宝列表页）
     */
    @GetMapping("/list")
    public Result<IPage<ProductSpuListVo>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String brandId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "default") String sort) {
        IPage<ProductSpuListVo> result = productService.spuPage(page, size, categoryId, brandId, keyword, sort);
        return Result.success(result);
    }


    /**
     * SPU 商品详情（含所有 SKU 变体、规格参数、销售属性面板）
     */
    @GetMapping("/detail/{spuId}")
    public Result<ProductSpuDetailVo> detail(@PathVariable String spuId)
            throws ExecutionException, InterruptedException {
        ProductSpuDetailVo vo = productService.spuDetail(spuId);
        return Result.success(vo);
    }

    /**
     * SKU 变体切换（轻量，仅返回单个 SKU 价格/库存/销售属性）
     */
    @GetMapping("/sku/{skuId}")
    public Result<Product> skuSwitch(@PathVariable String skuId) {
        Product sku = productService.skuSwitch(skuId);
        return Result.success(sku);
    }

    /**
     * SKU 级详情（旧版兼容，建议迁移到 /detail/{spuId}）
     */
    @Deprecated
    @GetMapping("/detail/sku/{skuId}")
    public Result<ProductDetailVo> detailBySku(@PathVariable String skuId)
            throws ExecutionException, InterruptedException {
        ProductDetailVo vo = productService.item(skuId);
        return Result.success(vo);
    }

    /**
     * SKU 简明信息（cart/order 内部使用）
     */
    @GetMapping("/simple/{id}")
    public Result<Product> simple(@PathVariable String id) {
        Product product = productService.detail(id);
        return Result.success(product);
    }


    /**
     * 商品上架 —— 全链路 SPU+SKU+图片+规格+销售属性+描述
     */
    @PostMapping("/create")
    public Result<ProductSpu> create(@RequestBody ProductCreateVo vo) {
        ProductSpu spu = productService.createSpu(vo);
        return Result.success(spu);
    }

    /**
     * SPU 更新 —— 就地更新 SPU+SKU，删重建子资源
     */
    @PutMapping("/update/{spuId}")
    public Result<ProductSpu> update(@PathVariable String spuId,
                                     @RequestBody ProductCreateVo vo) {
        ProductSpu spu = productService.updateSpu(spuId, vo);
        return Result.success(spu);
    }

    /**
     * SPU 下架 —— SPU + 所有 SKU 统一下架
     */
    @PutMapping("/off/{spuId}")
    public Result<String> offShelf(@PathVariable String spuId) {
        productService.offSpu(spuId);
        return Result.success("下架成功");
    }
}
