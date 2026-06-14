package com.myy.medihealth.product.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myy.medihealth.common.exception.BizException;
import com.myy.medihealth.product.entity.*;
import com.myy.medihealth.product.mapper.*;
import com.myy.medihealth.product.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 商品服务 —— 大厂级 SPU/SKU 架构
 *
 * 列表展示 SPU 卡（价格区间/属性标签），详情展示 SPU 下所有 SKU 变体，
 * 上架全链路写入 SPU→SKU→图片→规格→销售属性→描述
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    // ===== Mappers (读+写) =====
    private final ProductMapper productMapper;
    private final ProductSpuMapper productSpuMapper;
    private final ProductImageMapper productImageMapper;
    private final ProductDescMapper productDescMapper;
    private final ProductSpecValueMapper productSpecValueMapper;
    private final ProductSaleAttrValueMapper productSaleAttrValueMapper;

    // ===== 缓存 =====
    private final ProductCacheService productCacheService;

    // ===== 域服务（读） =====
    private final SkuImageService skuImageService;
    private final SpuInfoService spuInfoService;
    private final SpuDescService spuDescService;
    private final ProductSpecService productSpecService;
    private final SaleAttrService saleAttrService;
    private final CategoryService categoryService;
    private final BrandService brandService;

    // ===== 线程池 =====
    private final ExecutorService bizExecutor;

    // ================================================================
    //  列表
    // ================================================================

    /**
     * SPU 级商品列表 — 对应京东/淘宝列表页商品卡
     *
     * @param categoryId 分类ID（可选）
     * @param brandId    品牌ID（可选）
     * @param keyword    关键词搜索（可选）
     * @param sort       排序：default/sales/price_asc/price_desc
     */
    public IPage<ProductSpuListVo> spuPage(int page, int size,
            String categoryId, String brandId, String keyword, String sort) {
        IPage<ProductSpuListVo> result = productSpuMapper.selectSpuPage(
                new Page<>(page, size), categoryId, brandId, keyword, sort);

        // 批量补充 saleAttrTags（变体标签如"白色,黑色,红色"）
        List<String> spuIds = result.getRecords().stream()
                .map(ProductSpuListVo::getSpuId).toList();
        if (!spuIds.isEmpty()) {
            List<ProductSaleAttrValue> allTags = productSaleAttrValueMapper.selectList(
                    new LambdaQueryWrapper<ProductSaleAttrValue>()
                            .in(ProductSaleAttrValue::getSpuId, spuIds)
                            .orderByAsc(ProductSaleAttrValue::getSortOrder));
            Map<String, List<String>> tagMap = allTags.stream()
                    .collect(Collectors.groupingBy(
                            ProductSaleAttrValue::getSpuId,
                            LinkedHashMap::new,
                            Collectors.mapping(ProductSaleAttrValue::getAttrValue,
                                    Collectors.toList())));
            for (ProductSpuListVo vo : result.getRecords()) {
                vo.setSaleAttrTags(tagMap.getOrDefault(vo.getSpuId(), List.of()));
            }
        }
        return result;
    }

    // ================================================================
    //  详情
    // ================================================================

    /**
     * SPU 详情 — CompletableFuture 扇出 7 个数据源
     *
     * 调用方式：GET /medical/product/detail/{spuId}
     */
    public ProductSpuDetailVo spuDetail(String spuId)
            throws ExecutionException, InterruptedException {
        ProductSpuDetailVo vo = new ProductSpuDetailVo();

        // [1] SPU 基本信息（锚点）
        CompletableFuture<ProductSpu> spuFuture = CompletableFuture.supplyAsync(() -> {
            ProductSpu spu = spuInfoService.getById(spuId);
            if (spu == null) throw new BizException("商品不存在");
            vo.setSpuInfo(spu);
            return spu;
        }, bizExecutor);

        // [2] SKU 列表（含销售属性映射）
        CompletableFuture<Void> skusFuture = CompletableFuture.runAsync(() -> {
            List<Product> skus = productMapper.selectSkusBySpuId(spuId);
            // 批量查所有 SKU 的销售属性值
            List<String> skuIds = skus.stream().map(Product::getId).toList();
            Map<String, Map<String, String>> skuAttrMap = Collections.emptyMap();
            if (!skuIds.isEmpty()) {
                List<ProductSaleAttrValue> attrs = productSaleAttrValueMapper.selectList(
                        new LambdaQueryWrapper<ProductSaleAttrValue>()
                                .in(ProductSaleAttrValue::getSkuId, skuIds));
                skuAttrMap = attrs.stream()
                        .collect(Collectors.groupingBy(
                                ProductSaleAttrValue::getSkuId,
                                Collectors.toMap(
                                        ProductSaleAttrValue::getAttrName,
                                        ProductSaleAttrValue::getAttrValue,
                                        (a, b) -> a)));
            }
            Map<String, Map<String, String>> finalMap = skuAttrMap;
            vo.setSkus(skus.stream().map(sku -> {
                ProductSpuDetailVo.SkuBriefVo sv = new ProductSpuDetailVo.SkuBriefVo();
                sv.setSkuId(sku.getId());
                sv.setSkuName(sku.getName());
                sv.setPrice(sku.getPrice());
                sv.setStock(sku.getStock());
                sv.setStatus(sku.getStatus());
                sv.setPrescriptionRequired(sku.getPrescriptionRequired());
                sv.setImage(sku.getImage());
                sv.setSales(sku.getSales());
                sv.setSaleAttrMap(finalMap.getOrDefault(sku.getId(), Collections.emptyMap()));
                return sv;
            }).toList());
        }, bizExecutor);

        // [3] 图片
        CompletableFuture<Void> imagesFuture = CompletableFuture.runAsync(() -> {
            vo.setImages(skuImageService.getImagesBySpuId(spuId));
        }, bizExecutor);

        // [4] 品牌 + 分类（链式依赖 SPU）
        CompletableFuture<Void> brandCatFuture = spuFuture.thenAcceptAsync(spu -> {
            if (spu.getBrandId() != null) {
                vo.setBrand(brandService.getById(spu.getBrandId()));
            }
            if (spu.getCategoryId() != null) {
                vo.setCategory(categoryService.getById(spu.getCategoryId()));
            }
        }, bizExecutor);

        // [5] 描述
        CompletableFuture<Void> descFuture = CompletableFuture.runAsync(() -> {
            vo.setDescription(spuDescService.getBySpuId(spuId));
        }, bizExecutor);

        // [6] 规格参数分组
        CompletableFuture<Void> specFuture = CompletableFuture.runAsync(() -> {
            vo.setAttrGroups(productSpecService.getAttrGroupsBySpuId(spuId));
        }, bizExecutor);

        // [7] 销售属性选择面板
        CompletableFuture<Void> saleAttrFuture = CompletableFuture.runAsync(() -> {
            vo.setSaleAttrs(saleAttrService.getSaleAttrsBySpuId(spuId));
        }, bizExecutor);

        try {
            CompletableFuture.allOf(spuFuture, skusFuture, imagesFuture,
                    brandCatFuture, descFuture, specFuture, saleAttrFuture)
                    .get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("SPU详情聚合超时 spuId={}", spuId);
        }
        return vo;
    }

    /**
     * SKU 变体切换 — 轻量返回单个 SKU 的价格/库存
     *
     * 用于前端 SKU 选择面板切换变体时快速更新显示
     */
    public Product skuSwitch(String skuId) {
        Product sku = productCacheService.getProduct(skuId);
        if (sku == null) throw new BizException("商品不存在");
        return sku;
    }

    // ================================================================
    //  上架 / 更新 / 下架
    // ================================================================

    /**
     * 上架 —— 全链路写入
     *
     * 一次事务写入：SPU → SKU → 销售属性 → 图片 → 规格值 → 描述
     */
    @Transactional(rollbackFor = Exception.class)
    public ProductSpu createSpu(ProductCreateVo vo) {
        String spuId = IdUtil.fastSimpleUUID();
        LocalDateTime now = LocalDateTime.now();

        // 1. SPU
        ProductSpu spu = new ProductSpu();
        spu.setId(spuId);
        spu.setSpuName(vo.getSpuName());
        spu.setCategoryId(vo.getCategoryId());
        spu.setBrandId(vo.getBrandId());
        spu.setMainImage(vo.getMainImage());
        spu.setWeight(vo.getWeight() != null ? vo.getWeight() : 0);
        spu.setPublishStatus(1);
        spu.setAuditStatus(0);
        spu.setCreateTime(now);
        spu.setUpdateTime(now);
        productSpuMapper.insert(spu);

        // 2. SKU + 销售属性
        if (vo.getSkus() != null) {
            for (ProductCreateVo.SkuItem skuItem : vo.getSkus()) {
                String skuId = IdUtil.fastSimpleUUID();
                Product sku = new Product();
                sku.setId(skuId);
                sku.setSpuId(spuId);
                sku.setName(skuItem.getSkuName());
                sku.setPrice(skuItem.getPrice());
                sku.setStock(skuItem.getStock() != null ? skuItem.getStock() : 0);
                sku.setImage(skuItem.getImageUrl());
                sku.setImages(skuItem.getAdditionalImages() != null
                        ? skuItem.getAdditionalImages().toString() : null);
                sku.setCategory(vo.getCategoryId()); // 冗余分类 ID
                sku.setStatus(1);
                sku.setPrescriptionRequired(skuItem.getPrescriptionRequired() != null
                        ? skuItem.getPrescriptionRequired() : 0);
                sku.setSales(0);
                sku.setCreateTime(now);
                sku.setUpdateTime(now);
                productMapper.insert(sku);

                // 写入 SKU 销售属性
                if (skuItem.getSaleAttrs() != null) {
                    for (var sa : skuItem.getSaleAttrs()) {
                        ProductSaleAttrValue sav = new ProductSaleAttrValue();
                        sav.setId(IdUtil.fastSimpleUUID());
                        sav.setSpuId(spuId);
                        sav.setSkuId(skuId);
                        sav.setAttrId(sa.getAttrId());
                        sav.setAttrName(sa.getAttrName());
                        sav.setAttrValue(sa.getAttrValue());
                        sav.setSortOrder(sa.getSortOrder() != null ? sa.getSortOrder() : 0);
                        productSaleAttrValueMapper.insert(sav);
                    }
                }

                // 写入缓存
                productCacheService.cacheProduct(sku);
            }
        }

        // 3. 图片
        if (vo.getImages() != null) {
            for (var img : vo.getImages()) {
                ProductImage pi = new ProductImage();
                pi.setId(IdUtil.fastSimpleUUID());
                pi.setSpuId(spuId);
                pi.setSkuId(img.getSkuId());
                pi.setImageUrl(img.getImageUrl());
                pi.setSortOrder(img.getSortOrder() != null ? img.getSortOrder() : 0);
                pi.setIsDefault(img.getIsDefault() != null ? img.getIsDefault() : 0);
                productImageMapper.insert(pi);
            }
        }

        // 4. 规格值
        if (vo.getSpecValues() != null) {
            for (var sv : vo.getSpecValues()) {
                ProductSpecValue psv = new ProductSpecValue();
                psv.setId(IdUtil.fastSimpleUUID());
                psv.setSpuId(spuId);
                psv.setSpecId(sv.getSpecId());
                psv.setSpecName(sv.getSpecName());
                psv.setSpecValue(sv.getSpecValue());
                psv.setSortOrder(sv.getSortOrder() != null ? sv.getSortOrder() : 0);
                productSpecValueMapper.insert(psv);
            }
        }

        // 5. 描述
        if (vo.getDescription() != null) {
            ProductDesc desc = new ProductDesc();
            desc.setSpuId(spuId);
            desc.setDescription(vo.getDescription().getDescription());
            desc.setSpecifications(vo.getDescription().getSpecifications());
            desc.setAfterSale(vo.getDescription().getAfterSale());
            productDescMapper.insert(desc);
        }

        log.info("SPU上架成功 spuId={}, spuName={}, skuCount={}",
                spuId, vo.getSpuName(), vo.getSkus() != null ? vo.getSkus().size() : 0);
        return spu;
    }

    /**
     * 更新 SPU —— SPU 字段就地更新，子资源删重建，SKU 就地上插
     */
    @Transactional(rollbackFor = Exception.class)
    public ProductSpu updateSpu(String spuId, ProductCreateVo vo) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 更新 SPU
        ProductSpu spu = productSpuMapper.selectById(spuId);
        if (spu == null) throw new BizException("SPU不存在");
        spu.setSpuName(vo.getSpuName());
        spu.setCategoryId(vo.getCategoryId());
        spu.setBrandId(vo.getBrandId());
        spu.setMainImage(vo.getMainImage());
        if (vo.getWeight() != null) spu.setWeight(vo.getWeight());
        spu.setUpdateTime(now);
        productSpuMapper.updateById(spu);

        // 2. 删除旧的子资源（图片/规格值/销售属性/描述）
        deleteSubResources(spuId);

        // 3. 就地上插 SKU（保留已存在的 SKU ID 以保护购物车引用）
        if (vo.getSkus() != null) {
            // 先查出现有 SKU
            List<Product> existingSkus = productMapper.selectList(
                    new LambdaQueryWrapper<Product>().eq(Product::getSpuId, spuId));
            Set<String> existingIds = existingSkus.stream().map(Product::getId).collect(Collectors.toSet());
            Set<String> updatedIds = new HashSet<>();

            for (ProductCreateVo.SkuItem skuItem : vo.getSkus()) {
                // 按 SKU 名查找是否已存在（同名 SKU 更新）
                Product sku = existingSkus.stream()
                        .filter(s -> s.getName().equals(skuItem.getSkuName()))
                        .findFirst().orElse(null);

                if (sku != null) {
                    // 更新已有 SKU
                    sku.setPrice(skuItem.getPrice());
                    sku.setStock(skuItem.getStock() != null ? skuItem.getStock() : 0);
                    sku.setImage(skuItem.getImageUrl());
                    sku.setPrescriptionRequired(skuItem.getPrescriptionRequired() != null
                            ? skuItem.getPrescriptionRequired() : 0);
                    sku.setUpdateTime(now);
                    productMapper.updateById(sku);
                    productCacheService.evictCache(sku.getId());
                    productCacheService.cacheProduct(sku);
                    updatedIds.add(sku.getId());
                } else {
                    // 新增 SKU
                    String skuId = IdUtil.fastSimpleUUID();
                    sku = new Product();
                    sku.setId(skuId);
                    sku.setSpuId(spuId);
                    sku.setName(skuItem.getSkuName());
                    sku.setPrice(skuItem.getPrice());
                    sku.setStock(skuItem.getStock() != null ? skuItem.getStock() : 0);
                    sku.setImage(skuItem.getImageUrl());
                    sku.setCategory(vo.getCategoryId());
                    sku.setStatus(1);
                    sku.setPrescriptionRequired(skuItem.getPrescriptionRequired() != null
                            ? skuItem.getPrescriptionRequired() : 0);
                    sku.setSales(0);
                    sku.setCreateTime(now);
                    sku.setUpdateTime(now);
                    productMapper.insert(sku);
                    productCacheService.cacheProduct(sku);
                    updatedIds.add(skuId);
                }

                // 重建销售属性值
                if (skuItem.getSaleAttrs() != null) {
                    for (var sa : skuItem.getSaleAttrs()) {
                        ProductSaleAttrValue sav = new ProductSaleAttrValue();
                        sav.setId(IdUtil.fastSimpleUUID());
                        sav.setSpuId(spuId);
                        sav.setSkuId(sku.getId());
                        sav.setAttrId(sa.getAttrId());
                        sav.setAttrName(sa.getAttrName());
                        sav.setAttrValue(sa.getAttrValue());
                        sav.setSortOrder(sa.getSortOrder() != null ? sa.getSortOrder() : 0);
                        productSaleAttrValueMapper.insert(sav);
                    }
                }
            }

            // 下架不再存在的 SKU（不删除，保护订单引用）
            for (Product oldSku : existingSkus) {
                if (!updatedIds.contains(oldSku.getId())) {
                    oldSku.setStatus(0);
                    oldSku.setUpdateTime(now);
                    productMapper.updateById(oldSku);
                    productCacheService.evictCache(oldSku.getId());
                }
            }
        }

        // 4. 重新插入图片/规格值/描述
        if (vo.getImages() != null) {
            for (var img : vo.getImages()) {
                ProductImage pi = new ProductImage();
                pi.setId(IdUtil.fastSimpleUUID());
                pi.setSpuId(spuId);
                pi.setSkuId(img.getSkuId());
                pi.setImageUrl(img.getImageUrl());
                pi.setSortOrder(img.getSortOrder() != null ? img.getSortOrder() : 0);
                pi.setIsDefault(img.getIsDefault() != null ? img.getIsDefault() : 0);
                productImageMapper.insert(pi);
            }
        }

        if (vo.getSpecValues() != null) {
            for (var sv : vo.getSpecValues()) {
                ProductSpecValue psv = new ProductSpecValue();
                psv.setId(IdUtil.fastSimpleUUID());
                psv.setSpuId(spuId);
                psv.setSpecId(sv.getSpecId());
                psv.setSpecName(sv.getSpecName());
                psv.setSpecValue(sv.getSpecValue());
                psv.setSortOrder(sv.getSortOrder() != null ? sv.getSortOrder() : 0);
                productSpecValueMapper.insert(psv);
            }
        }

        if (vo.getDescription() != null) {
            ProductDesc desc = new ProductDesc();
            desc.setSpuId(spuId);
            desc.setDescription(vo.getDescription().getDescription());
            desc.setSpecifications(vo.getDescription().getSpecifications());
            desc.setAfterSale(vo.getDescription().getAfterSale());
            productDescMapper.insert(desc);
        }

        log.info("SPU更新成功 spuId={}", spuId);
        return spu;
    }

    /**
     * 下架整个 SPU（SPU + 所有 SKU 全部 status=0）
     */
    @Transactional(rollbackFor = Exception.class)
    public void offSpu(String spuId) {
        ProductSpu spu = productSpuMapper.selectById(spuId);
        if (spu == null) throw new BizException("SPU不存在");
        spu.setPublishStatus(0);
        spu.setUpdateTime(LocalDateTime.now());
        productSpuMapper.updateById(spu);

        List<Product> skus = productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getSpuId, spuId));
        for (Product sku : skus) {
            sku.setStatus(0);
            sku.setUpdateTime(LocalDateTime.now());
            productMapper.updateById(sku);
            productCacheService.evictCache(sku.getId());
        }
        log.info("SPU下架成功 spuId={}, affectedSkus={}", spuId, skus.size());
    }

    private void deleteSubResources(String spuId) {
        // 删除图片
        productImageMapper.delete(new LambdaQueryWrapper<ProductImage>().eq(ProductImage::getSpuId, spuId));
        // 删除规格值
        productSpecValueMapper.delete(new LambdaQueryWrapper<ProductSpecValue>().eq(ProductSpecValue::getSpuId, spuId));
        // 删除销售属性值
        productSaleAttrValueMapper.delete(new LambdaQueryWrapper<ProductSaleAttrValue>().eq(ProductSaleAttrValue::getSpuId, spuId));
        // 删除描述
        productDescMapper.deleteById(spuId);
    }

    // ================================================================
    //  向后兼容方法（cart / order / 旧前端使用）
    // ================================================================

    /**
     * SKU 级商品详情（CompletableFuture 扇出，兼容旧版 /detail/{skuId} 调用）
     */
    public ProductDetailVo item(String skuId) throws ExecutionException, InterruptedException {
        ProductDetailVo vo = new ProductDetailVo();

        CompletableFuture<Product> skuFuture = CompletableFuture.supplyAsync(() -> {
            Product skuInfo = productCacheService.getProduct(skuId);
            if (skuInfo == null) throw new BizException("商品不存在");
            vo.setSkuInfo(skuInfo);
            return skuInfo;
        }, bizExecutor);

        CompletableFuture<Void> imageFuture = CompletableFuture.runAsync(() -> {
            vo.setImages(skuImageService.getImagesBySkuId(skuId));
        }, bizExecutor);

        CompletableFuture<Void> saleAttrFuture = skuFuture.thenAcceptAsync(sku -> {
            String spuId = sku.getSpuId();
            if (spuId != null) vo.setSaleAttrs(saleAttrService.getSaleAttrsBySpuId(spuId));
        }, bizExecutor);

        CompletableFuture<Void> descFuture = skuFuture.thenAcceptAsync(sku -> {
            String spuId = sku.getSpuId();
            if (spuId != null) vo.setDescription(spuDescService.getBySpuId(spuId));
        }, bizExecutor);

        CompletableFuture<Void> specFuture = skuFuture.thenAcceptAsync(sku -> {
            String spuId = sku.getSpuId();
            if (spuId != null) vo.setAttrGroups(productSpecService.getAttrGroupsBySpuId(spuId));
        }, bizExecutor);

        CompletableFuture<Void> spuInfoFuture = skuFuture.thenAcceptAsync(sku -> {
            String spuId = sku.getSpuId();
            if (spuId == null) return;
            ProductSpu spuInfo = spuInfoService.getById(spuId);
            vo.setSpuInfo(spuInfo);
            if (spuInfo != null) {
                if (spuInfo.getBrandId() != null) vo.setBrand(brandService.getById(spuInfo.getBrandId()));
                if (spuInfo.getCategoryId() != null) vo.setCategory(categoryService.getById(spuInfo.getCategoryId()));
            }
        }, bizExecutor);

        try {
            CompletableFuture.allOf(imageFuture, saleAttrFuture, descFuture, specFuture, spuInfoFuture)
                    .get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("商品详情聚合超时 skuId={}", skuId);
        }
        return vo;
    }

    /** SKU 简明信息（仅缓存查） */
    public Product detail(String id) {
        Product product = productCacheService.getProduct(id);
        if (product == null) throw new BizException("商品不存在");
        return product;
    }

    /** 批量 SKU 信息（购物车/订单使用） */
    public List<Product> batchDetail(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return productCacheService.getProducts(new HashSet<>(ids))
                .values().stream().toList();
    }

    /** 旧版 SKU 级分页（向后兼容） */
    public IPage<Product> page(int page, int size, String category, String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        if (StrUtil.isNotBlank(category)) wrapper.eq(Product::getCategory, category);
        if (StrUtil.isNotBlank(keyword)) wrapper.like(Product::getName, keyword);
        wrapper.orderByDesc(Product::getCreateTime);
        return productMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 旧版单 SKU 上架（向后兼容） */
    public Product create(ProductSaveVo vo) {
        Product product = new Product();
        product.setId(IdUtil.fastSimpleUUID());
        product.setSpuId(vo.spuId());
        product.setName(vo.name());
        product.setDescription(vo.description());
        product.setPrice(vo.price());
        product.setStock(vo.stock());
        product.setImage(vo.image());
        product.setImages(vo.images());
        product.setCategory(vo.category());
        product.setStatus(vo.status() != null ? vo.status() : 1);
        product.setSales(0);
        product.setPrescriptionRequired(vo.prescriptionRequired() != null ? vo.prescriptionRequired() : 0);
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        productMapper.insert(product);
        productCacheService.cacheProduct(product);
        return product;
    }

    /** 旧版单 SKU 更新（向后兼容） */
    public Product update(String id, ProductSaveVo vo) {
        Product product = productMapper.selectById(id);
        if (product == null) throw new BizException("商品不存在");
        BeanUtil.copyProperties(vo, product, "id", "createTime", "sales");
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        productCacheService.evictCache(id);
        productCacheService.cacheProduct(product);
        return product;
    }

    /** 旧版单 SKU 下架（向后兼容） */
    public Product offShelf(String id) {
        Product product = productMapper.selectById(id);
        if (product == null) throw new BizException("商品不存在");
        product.setStatus(0);
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        productCacheService.evictCache(id);
        return product;
    }
}
