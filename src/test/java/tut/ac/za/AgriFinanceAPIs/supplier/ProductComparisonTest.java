package tut.ac.za.AgriFinanceAPIs.supplier;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import tut.ac.za.AgriFinanceAPIs.supplier.dto.ProductRequest;

class ProductComparisonTest {
    @Test void preservesOmittedMetadataAndAcceptsOnlyCompleteSpecifications() {
        var p=new SupplierProduct();p.setComparisonKey("maize-variety-A");p.setPackSize(BigDecimal.ONE);p.setPackUnit("kg");
        var request=new ProductRequest();
        SupplierService.setComparison(p,request);
        assertThat(p.getComparisonKey()).isEqualTo("maize-variety-A");
        request.setComparisonKey("maize-variety-B");
        assertThatThrownBy(() -> SupplierService.setComparison(p,request)).isInstanceOf(RuntimeException.class);
        request.setPackSize(new BigDecimal("5"));request.setPackUnit("kg");
        SupplierService.setComparison(p,request);
        assertThat(p.getPackSize()).isEqualByComparingTo("5");
        request.setPackSize(new BigDecimal("0"));
        assertThatThrownBy(() -> SupplierService.setComparison(p,request)).isInstanceOf(RuntimeException.class);
    }
    @Test void explicitlyClearsMetadata() {
        var p=new SupplierProduct();p.setComparisonKey("old");p.setPackUnit("kg");p.setPackSize(BigDecimal.ONE);
        var request=new ProductRequest();request.setComparisonKey("");request.setPackUnit("");
        SupplierService.setComparison(p,request);
        assertThat(p.getComparisonKey()).isNull();assertThat(p.getPackSize()).isNull();assertThat(p.getPackUnit()).isNull();
    }
}
