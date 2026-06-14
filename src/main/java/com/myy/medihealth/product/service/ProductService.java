package com.myy.medihealth.product.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myy.medihealth.common.exception.BizException;
import com.myy.medihealth.product.entity.Product;
import com.myy.medihealth.product.mapper.ProductMapper;
import com.myy.medihealth.product.vo.ProductSaveVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;

    public IPage<Product> page(int page, int size, String category, String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, 1);
        if (StrUtil.isNotBlank(category)) {
            wrapper.eq(Product::getCategory, category);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Product::getName, keyword);
        }
        wrapper.orderByDesc(Product::getCreateTime);
        return productMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Product detail(String id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException("商品不存在");
        }
        return product;
    }

    public Product create(ProductSaveVo vo) {
        Product product = new Product();
        product.setId(IdUtil.fastSimpleUUID());
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
        return product;
    }

    public Product update(String id, ProductSaveVo vo) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException("商品不存在");
        }
        BeanUtil.copyProperties(vo, product, "id", "createTime", "sales");
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    public Product offShelf(String id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException("商品不存在");
        }
        product.setStatus(0);
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }
}
