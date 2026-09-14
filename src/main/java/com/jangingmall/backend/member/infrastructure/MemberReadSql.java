package com.jangingmall.backend.member.infrastructure;

final class MemberReadSql {
    static final String VISIBLE_PRODUCT = "p.status IN ('ON_SALE','SOLD_OUT') AND m.status='ACTIVE' AND a.certification_status='APPROVED'";
    static final String PRODUCT_FROM = "product p JOIN artisan_profile a ON a.artisan_id=p.artisan_id JOIN member m ON m.member_id=a.artisan_id";
    static final String IMAGE = """
        COALESCE((SELECT jsonb_agg(jsonb_build_object('url', :cdn || '/' || (iu.variants->v.key->>'objectKey'),
          'width', v.width, 'height', round(v.width::numeric / NULLIF(iu.source_width,0) * iu.source_height), 'format','webp') ORDER BY v.width)
          FROM product_image pi JOIN image_upload iu ON iu.image_id=pi.image_id
          CROSS JOIN (VALUES ('320w',320),('640w',640),('1280w',1280)) AS v(key,width)
          WHERE pi.product_id=p.product_id AND pi.display_order=0 AND iu.consumed=true AND jsonb_exists(iu.variants,v.key)), '[]'::jsonb)
        """;
    static final String PRODUCT = """
        jsonb_build_object('productId',p.product_id,'name',p.name,'price',p.price,'thumbnail',%s,
          'status',p.status,'category',p.category_code,'subcategory',p.subcategory_code,'color',p.color,
          'giftTheme',p.gift_theme,'rating',(SELECT avg(pr.rating) FROM product_review pr WHERE pr.product_id=p.product_id),
          'isLimited',p.is_limited,'isCustomOrder',p.is_custom_order,'isSingleItem',p.is_single_item,
          'isNew',p.created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days','hasGiftWrap',p.has_gift_wrap,
          'hasOptions',EXISTS(SELECT 1 FROM product_option_group pog WHERE pog.product_id=p.product_id),
          'purposeTags',COALESCE(to_jsonb(p.purpose_tags),'[]'::jsonb),
          'primaryBadge',CASE WHEN p.is_limited THEN 'LIMITED'
            WHEN p.created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days' THEN 'NEW'
            WHEN p.popularity_score > 0 THEN 'POPULAR' END,
          'artisanId',a.artisan_id,'artisanName',a.business_name)
        """.formatted(IMAGE);
    static final String PRODUCT_COUNT = "(SELECT count(*) FROM product p WHERE p.artisan_id=a.artisan_id AND p.status IN ('ON_SALE','SOLD_OUT'))";
    static final String ARTISAN = """
        jsonb_build_object('artisanId',a.artisan_id,'businessName',a.business_name,'introduction',a.introduction,
          'profileImageUrl',a.profile_image_url,'certificationLevel',a.certification_level,'isOrganization',a.is_organization,
          'certificationStatus',a.certification_status,'category',a.category_code,'region',a.region,'careerYears',a.career_years,
          'productCount',%s,'topProducts',COALESCE((SELECT jsonb_agg(t.payload) FROM
            (SELECT jsonb_build_object('productId',p.product_id,'thumbnail',%s) AS payload
             FROM product p WHERE p.artisan_id=a.artisan_id AND p.status IN ('ON_SALE','SOLD_OUT')
             ORDER BY p.popularity_score DESC,p.product_id DESC LIMIT 3) t),'[]'::jsonb),
          'certifiedYear',a.certified_year,'lineage',a.lineage,'quote',a.quote,'bio',a.bio,'videoUrl',a.video_url,
          'careerTimeline',COALESCE((SELECT jsonb_agg(jsonb_build_object('year',ct.year,'title',ct.title,'description',ct.description)
            ORDER BY ct.year,ct.timeline_id) FROM career_timeline ct WHERE ct.artisan_id=a.artisan_id),'[]'::jsonb))
        """.formatted(PRODUCT_COUNT, IMAGE);
    static final String ARTISAN_FROM = "artisan_profile a JOIN member m ON m.member_id=a.artisan_id";
    static final String VISIBLE_ARTISAN = "m.status='ACTIVE' AND m.role='ARTISAN' AND a.certification_status='APPROVED'";
    static final String ORDER = "jsonb_build_object('orderId',o.order_id,'orderNumber',o.order_number,'status',o.status,'totalAmount',o.total_amount,'createdAt',o.created_at)";
    static final String ORDER_DETAIL = ORDER + """
        || jsonb_build_object('items',COALESCE((SELECT jsonb_agg(jsonb_build_object('orderItemId',i.order_item_id,'productId',i.product_id,
            'productName',i.product_name_snapshot,'price',i.price_snapshot,'quantity',i.quantity) ORDER BY i.order_item_id)
            FROM order_item i WHERE i.order_id=o.order_id),'[]'::jsonb),
          'address',jsonb_build_object('addressId',o.address_id,'recipientName',o.recipient_name,'phone',o.recipient_phone,
            'zipCode',o.zip_code,'address1',o.address1,'address2',o.address2,'isDefault',false))
        """;
    private MemberReadSql() {}
}
