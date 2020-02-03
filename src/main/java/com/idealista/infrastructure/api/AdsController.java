package com.idealista.infrastructure.api;

import java.util.*;

import com.idealista.infrastructure.persistence.AdVO;
import com.idealista.infrastructure.persistence.InMemoryPersistence;
import com.idealista.infrastructure.persistence.PictureVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ad-ranking-challenge")
public class AdsController {

    @Autowired
    private InMemoryPersistence repository;

    @GetMapping(path = "/qualityList", produces= MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<QualityAd>> qualityListing() {
        List<AdVO> irrelevantAdsList    = repository.getIrrelevantAds();
        List<QualityAd> qualityAdList   = new ArrayList<QualityAd>();

        for (AdVO adInfo : irrelevantAdsList) {
            QualityAd qualityAd = new QualityAd();

            qualityAd.setId(adInfo.getId());
            qualityAd.setTypology(adInfo.getTypology());
            qualityAd.setDescription(adInfo.getDescription());
            qualityAd.setGardenSize(adInfo.getGardenSize());
            qualityAd.setHouseSize(adInfo.getHouseSize());
            qualityAd.setScore(adInfo.getScore());
            qualityAd.setIrrelevantSince(adInfo.getIrrelevantSince());

            List<String> pictureUrlsList = new ArrayList<String>();
            List<PictureVO> adPicturesList = repository.getAdPrictures(adInfo);

            for (PictureVO pic : adPicturesList) {
                pictureUrlsList.add(pic.getUrl());
            }

            qualityAd.setPictureUrls(pictureUrlsList);
            qualityAdList.add(qualityAd);
        }

        return new ResponseEntity<>(qualityAdList, HttpStatus.OK);
    }

    @GetMapping(path = "/publicListing", produces= MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PublicAd>> publicListing() {
        List<AdVO> relevantAdsList      = repository.getRelevantAds();
        List<PublicAd> publicAdsList    = new ArrayList<PublicAd>();

        for (AdVO adInfo : relevantAdsList) {
            PublicAd publicAd = new PublicAd();

            publicAd.setId(adInfo.getId());
            publicAd.setTypology(adInfo.getTypology());
            publicAd.setDescription(adInfo.getDescription());
            publicAd.setGardenSize(adInfo.getGardenSize());
            publicAd.setHouseSize(adInfo.getHouseSize());

            List<String> pictureUrlsList = new ArrayList<String>();
            List<PictureVO> adPicturesList = repository.getAdPrictures(adInfo);

            for (PictureVO pic : adPicturesList) {
                pictureUrlsList.add(pic.getUrl());
            }

            publicAd.setPictureUrls(pictureUrlsList);
            publicAdsList.add(publicAd);
        }

        return new ResponseEntity<>(publicAdsList, HttpStatus.OK);
    }

    @PutMapping("/calculateScore/{id}")
    public ResponseEntity<Void> calculateScore(@PathVariable Integer id) {
        //Consulta de la información del anuncio
        AdVO adInfo = repository.getAdById(id);

        if (adInfo == null)
            return ResponseEntity.notFound().build();

        List<PictureVO> adPicturesList = repository.getAdPrictures(adInfo);

        //Definición de los valores base a trabajar
        Integer totalScore     = 0;
        Boolean hasPictures    = !adPicturesList.isEmpty();
        Boolean hasDescription = adInfo.getDescription() != null && !adInfo.getDescription().isEmpty();

        //Cálculo de puntación por fotos agregadas al anuncio.
        if (hasPictures) {
            //Cada foto que tenga el anuncio proporciona 20 puntos si es una foto de alta resolución (HD) o 10 si no lo es
            for (PictureVO pic : adPicturesList)
                totalScore += pic.getQuality().equals("HD") ? 20 : 10;
        }
        else {
            //Si el anuncio no tiene ninguna foto se restan 10 puntos
            totalScore -= 10;
        }

        if (hasDescription) {
            //Que el anuncio tenga un texto descriptivo suma 5 puntos.
            totalScore += 5;

            // Creación de tokenizer para manipulación de la descripción
            StringTokenizer stok    = new StringTokenizer(adInfo.getDescription().replaceAll("[|;:,.'\"¡!¿?]", " "), " ");
            Integer totalDescWords  = stok.countTokens();

            //En el caso de los pisos,
            //la descripción aporta 10 puntos si tiene entre 20 y 49 palabras o 30 puntos si tiene 50 o mas palabras
            if (adInfo.getTypology().equals("FLAT") && totalDescWords >= 20) {
                totalScore += (totalDescWords >= 50) ? 30 : 10;
            }
            //En el caso de los chalets,
            //si tiene mas de 50 palabras, añade 20 puntos.
            else if (adInfo.getTypology().equals("CHALET") && totalDescWords > 50) {
                totalScore += 20;
            }

            //Que las siguientes palabras aparezcan en la descripción añaden 5 puntos cada una:
            //Luminoso, Nuevo, Céntrico, Reformado, Ático.
            List<String> keyWordsList = Arrays.asList("Luminoso", "Nuevo", "Reformado", "Ático");
            for (String keyWord : keyWordsList) {
                if(adInfo.getDescription().toLowerCase().contains(keyWord.toLowerCase())) {
                    totalScore += 5;
                }
            }
        }

        //Validación del nivel de completación del anuncio:
        if ((hasPictures && hasDescription && ((adInfo.getTypology().equals("FLAT") && adInfo.getHouseSize() != null)
                || (adInfo.getTypology().equals("CHALET") && adInfo.getHouseSize() != null && adInfo.getGardenSize() != null)))
                || (adInfo.getTypology().equals("GARAGE") && adInfo.getHouseSize() != null && hasPictures)) {

            totalScore += 40;
        }

        //Un anuncio se considera irrelevante si tiene una puntación inferior a 40 puntos.
        //Si esto se cumple, actualizar con el valor de la fecha actual
        if (totalScore < 40 && adInfo.getIrrelevantSince() == null) {
            adInfo.setIrrelevantSince(new Date());
        }
        else if (totalScore >= 40 && adInfo.getIrrelevantSince() != null) {
            adInfo.setIrrelevantSince(null);
        }

        adInfo.setScore(totalScore);

        if(!repository.saveAd(adInfo)) {
            ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().build();
    }

}
