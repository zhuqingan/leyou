package com.leyou.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.omg.CORBA.INVALID_ACTIVITY;

/**
 * @author zhuqa
 * @projectName leyou
 * @description: TODO
 * @date 2019/10/17 23:17
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public enum ExceptionEnum {
    //枚举必须放在属性前方，多个枚举用逗号隔开，最后一个枚举用分号
    PRICE_CREATE_BE_NULL(400, "价格不能为空！"),
    GOODS_ID_CANNOT_BE_NULL(400, "商品id不能为空"),
    CATEGORY_NOT_FOUND(404, "商品分类没查到"),
    GOODS_NOT_FOUND(404, "商品分类没查到"),
    SPEC_GROUP_NOT_FOUND(500, "商品分类组不存在"),
    SPEC_PARAM_NOT_FOUND(500, "商品规格不存在"),
    GOODS_SAVE_ERROR(500, "新增商品失败"),
    BRAND_NOT_FOUND(404, "品牌不存在"),
    CATEGORY_BRAND_SAVE_ERROR(500, "新增品牌分类失败"),
    UPLOAD_FILE_ERROR(500, "文件上传失败"),
    INVALID_FILE_TYPE(500, "无效的文件类型"),
    GOODS_UPDATE_ERROR(500, "更新商品失败"),
    INVALID_USER_DATA_TYPE(400,"用户数据类型无效"),
    UNAUTHORIZED(403,"用户信息已过期"),
    BRAND_SAVE_ERROR(500, "新增品牌失败");

    private int code;
    private String msg;
}
