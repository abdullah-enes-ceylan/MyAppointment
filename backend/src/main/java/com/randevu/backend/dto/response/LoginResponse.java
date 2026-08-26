package com.randevu.backend.dto.response;

// Login yaniti. Eskiden AuthController elle kurulan bir Map<String,String>
// donuyordu ("ekstra dosya acmamak icin" diye not dusulmustu) -- ama bunun
// bedeli, API sozlesmesinin hicbir yerde tanimli olmamasiydi: yanitta hangi
// alanlarin oldugunu gormek icin controller'in govdesini okumak gerekiyordu.
// Record olarak tanimlamak hem sozlesmeyi acik ediyor hem de ileride
// (ornegin refresh token eklendiginde) alanin nereye ekleneceğini
// belirsizlikten cikariyor.
public record LoginResponse(String token) {
}
